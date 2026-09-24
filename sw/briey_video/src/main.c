#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>

#include "murax.h"

#define CORE_MHZ 64
#define CORE_HZ 64000000
#define I2C_CTRL 0xF0040000
#define DDR2_BASE 0x40000000

#define RES_X (1280 / 2)
#define RES_Y 720

__attribute__ ((section (".noinit"))) __attribute__ ((aligned (4*8))) uint32_t vgaFramebuffer[RES_Y][RES_X];

extern void flushDataCache(uint32_t dummy);

//#define _CHECK_MEM

void print(const char *str) {
	while (*str) {
		uart_write(UART, *str);
		str++;
	}
}
void println(const char *str) {
	print(str);
	uart_write(UART, '\n');
}

void delay(uint32_t loops) {
	for (int i = 0; i < loops; i++) {
		int tmp = GPIO_A->OUTPUT;
	}
}

typedef void (*irq_handler_t)(void);

void i2c_init() {
	//I2C init
	I2c_Config i2c;
	i2c.samplingClockDivider = 3;
	i2c.timeout = CORE_HZ / 1000;    //1 ms;
	i2c.tsuDat = CORE_HZ / 2000000; //500 ns

	i2c.tLow = CORE_HZ / 200000;  //1.25 us
	i2c.tHigh = CORE_HZ / 200000; //1.25 us
	i2c.tBuf = CORE_HZ / 100000;  //2.5 us

	i2c_applyConfig(I2C_CTRL, &i2c);
}

uint64_t read_cycles(void) {
	uint32_t cycles_high, cycles_low;
	cycles_high = SYSTIMER->SysTimVal_H;
	cycles_low = SYSTIMER->SysTimVal_L;
	return (((uint64_t) cycles_high) << 32) | cycles_low;
}

void uart_init(void) {
	Uart_Config uartCfg;

	uartCfg.clockDivider = (CORE_HZ / 8 / 115200) - 1;
	uartCfg.dataLength = 8;
	uartCfg.parity = NONE;
	uartCfg.stop = ONE;
	uart_applyConfig(UART, &uartCfg);
}

void vga_init(void) {
	vga_stop(VGA_BASE);
	VGA_BASE->TIMING = vga_h1280_v720;
	VGA_BASE->FRAME_SIZE = RES_X * RES_Y * 4 - 1;
	VGA_BASE->FRAME_BASE = (uint32_t) vgaFramebuffer;
	vga_run(VGA_BASE);
}

void print_uint64(uint64_t num) {
	char buffer[21];
	int i = 0;

	// Handle 0 explicitly
	if (num == 0) {
		uart_write(UART, '0');
		return;
	}

	// Extract digits backward
	while (num > 0) {
		buffer[i++] = (num % 10) + '0';
		num /= 10;
	}

	// Print the buffer in reverse order
	for (int j = i - 1; j >= 0; j--) {
		uart_write(UART, buffer[j]);
	}
}

void print_uint32(uint32_t num) {
	char buffer[11];
	int i = 0;

	// Handle 0 explicitly
	if (num == 0) {
		uart_write(UART, '0');
		return;
	}

	// Extract digits backward
	while (num > 0) {
		buffer[i++] = (num % 10) + '0';
		num /= 10;
	}

	// Print the buffer in reverse order
	for (int j = i - 1; j >= 0; j--) {
		uart_write(UART, buffer[j]);
	}
}

void print_hex32(uint32_t num) {

	uint32_t mask = 0xF0000000;

	// Print the buffer in reverse order
	for (int j = 28; j >= 0; j -= 4) {
		uint32_t b = (num & mask) >> j;
		mask = mask >> 4;
		uint8_t bb = (b > 9) ? b + 0x37 : b + 0x30;
		uart_write(UART, bb);
	}
}

uint32_t generate_dynamic_pattern(uint32_t addr, uint32_t pass_seed) {
	uint32_t inverted_addr = ~addr;
	// Mix the address, its inverse, and the unique pass seed
	return (addr ^ 0x5A5A5A5A) + inverted_addr + pass_seed;
}

volatile uint32_t frame_update = 0;

void main() {

#ifdef _CHECK_MEM
	volatile uint32_t *ptr;
	uint32_t value, expect;
	uint64_t start, end, delta;
	uint32_t wr_ptr = 0;
	uint32_t rd_ptr = 0;
	const char *header_msg[] = { "DDR ONE stuck", "DDR ZERO stuck",
			"DDR BIT-FLIP 55->?", "DDR BIT-FLIP AA->?", "DDR BIT-FLIP 5A->?",
			"DDR BIT-FLIP A5->?" };
	const uint32_t test_pattern[] = { 0x00000000, 0xFFFFFFFF, 0x55555555,
			0xAAAAAAAA, 0x5A5A5A5A, 0xA5A5A5A5 };

	// Changing seeds for different passes
	uint32_t wr_pass_seed = 0xAAAAAAA5;
	uint32_t rd_pass_seed = 0xAAAAAAA5;
#endif

	uart_init();
	println("AGM AG16KF256");
	println("Load from JTAG at Windows");

	interruptCtrl_init(TIMER_INTERRUPT);
	prescaler_init(TIMER_PRESCALER);
	timer_init(TIMER_A);
	timer_init(TIMER_B);

	GPIO_A->OUTPUT_ENABLE = 0x0000000F;
	GPIO_A->OUTPUT = 0x0000000F;

	print("DDR status ");
	uart_write(UART, 0x30 + ((GPIO_A->INPUT & 0xF0) >> 4));
	uart_write(UART, '\n');

//	i2c_init();

	TIMER_PRESCALER->LIMIT = 33333 - 1;
	TIMER_A->LIMIT = 5000 - 1;
	TIMER_A->VALUE = 0;
	TIMER_A->CLEARS_TICKS = 0x00010002;

	TIMER_B->LIMIT = 2500 - 1;
	TIMER_B->VALUE = 0;
	TIMER_B->CLEARS_TICKS = 0x00010002;

	TIMER_INTERRUPT->PENDINGS = 0xF;
	TIMER_INTERRUPT->MASKS = 0x3;

	vga_init();

	uint32_t index = 0;
	uint32_t maske[3] = { 0x1F, 0x3F, 0x1F };
	uint32_t shift[3] = { 11, 5, 0 };
	while (1) {

#ifdef _CHECK_MEM
		for (uint32_t j = 0; j < 6; j++) {
			println(header_msg[j]);
			ptr = (volatile uint32_t*) DDR2_BASE;

			start = read_cycles();
			for (uint32_t i = 0; i < 256 * 1024 * 1024 / 4; i++) {
				*ptr++ = test_pattern[j];
			}
			end = read_cycles();
			delta = (end - start) / CORE_MHZ;
			print("Execution time us: ");
			print_uint64(delta);
			print(".\n");

			ptr = (volatile uint32_t*) DDR2_BASE;
			start = read_cycles();
			for (uint32_t i = 0; i < 256 * 1024 * 1024 / 4; i++) {
				value = *ptr++;
				if (value != test_pattern[j]) {
					print(header_msg[j]);
					print(" @ ");
					print_hex32(i);
					print(" RD: ");
					print_hex32(value);
					print(" !\n");
				}
			}
			end = read_cycles();
			delta = (end - start) / CORE_MHZ;
			print("Execution time us: ");
			print_uint64(delta);
			print(".\n");
		}

		println("DDR2 XOR Address Test.");
		ptr = (volatile uint32_t*) DDR2_BASE;
		start = read_cycles();
		for (uint32_t i = 0; i < 256 * 1024 * 1024 / 4; i++) {
			*ptr++ = generate_dynamic_pattern(i, wr_pass_seed);
			;
		}

		end = read_cycles();
		delta = (end - start) / CORE_MHZ;
		print("Execution time us: ");
		print_uint64(delta);
		print(".\n");
		wr_pass_seed = ~wr_pass_seed;

		ptr = (volatile uint32_t*) DDR2_BASE;
		start = read_cycles();
		for (uint32_t i = 0; i < 256 * 1024 * 1024 / 4; i++) {
			value = *ptr++;
			expect = generate_dynamic_pattern(i, rd_pass_seed);
			if (value != expect) {
				print("DDR mismatch @");
				print_hex32(i);
				print(" RD: ");
				print_hex32(value);
				print(" =/= ");
				print_hex32(expect);
				print(" !\n");
			}
		}

		end = read_cycles();
		delta = (end - start) / CORE_MHZ;
		print("Execution time us: ");
		print_uint64(delta);
		print(".\n");
		rd_pass_seed = ~rd_pass_seed;

		println("--- DDR R/W Done ---");
#endif

		if (frame_update) {
			frame_update = 0;
			uint32_t *ptr = &vgaFramebuffer[0][0];
			for (uint32_t y = 0; y < RES_Y; y++) {
				for (uint32_t i = 0; i < RES_X; i++) {
					// Calculate original pixel column positions (0 to 1279)
					uint32_t x0 = i << 1;
					uint32_t x1 = x0 + 1;

					// Compute 16-bit RGB565 values for each pixel
					uint16_t pixel0 = ((x0 & y) & maske[index]) << shift[index];
					uint16_t pixel1 = ((x1 & y) & maske[index]) << shift[index];

					// Store pixel pair: pixel0 in lower 16 bits, pixel1 in upper 16 bits
					vgaFramebuffer[y][i] = ((uint32_t) pixel1 << 16) | pixel0;
				}
			}
			index = (index + 1) % 3;
			flushDataCache(0);
			println("--- Video Frame Update ---");
		}

//	    i2c_masterStartBlocking(I2C_CTRL);
//	    i2c_txByte(I2C_CTRL, 0x42);
//	    i2c_txNackBlocking(I2C_CTRL);
//	    i2c_rxAck(I2C_CTRL); // Optional check
//	    i2c_txByte(I2C_CTRL, 0x95);
//	    i2c_txNackBlocking(I2C_CTRL);
//	    i2c_rxAck(I2C_CTRL); // Optional check
//	    i2c_txByte(I2C_CTRL, 0x64);
//	    i2c_txNackBlocking(I2C_CTRL);
//	    i2c_rxNack(I2C_CTRL); // Optional check
//	    i2c_masterStopBlocking(I2C_CTRL);
	}
}

void timer_a_handler(void) {
	GPIO_A->OUTPUT ^= 0x3;
	TIMER_INTERRUPT->PENDINGS = 1;
	frame_update = 1;
}

void timer_b_handler(void) {
	GPIO_A->OUTPUT ^= 0xC;
	TIMER_INTERRUPT->PENDINGS = 2;
}

irq_handler_t irq_vector_table[32] = { [0] = timer_a_handler, [1
		] = timer_b_handler,
// Bits 2 to 31 will fall back to our safety handler
		};

void irqCallback() {
	uint32_t active_interrupts = TIMER_INTERRUPT->PENDINGS;

	// Loop through the active bits to find which peripheral fired
	for (int id = 0; id < 32; id++) {
		if (active_interrupts & (1 << id)) {
			// Check if a valid function pointer is registered
			if (irq_vector_table[id] != 0) {
				irq_vector_table[id](); // Execute the split function!
			} else {

			}
		}
	}
}
