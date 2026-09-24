#ifndef __MURAX_H__
#define __MURAX_H__

#include "timer.h"
#include "prescaler.h"
#include "interrupt.h"
#include "gpio.h"
#include "uart.h"
#include "i2c.h"
#include "spi.h"
#include "vga.h"


#define GPIO_A    		((Gpio_Reg*)		0xF0000000)
#define GPIO_B    		((Gpio_Reg*)		0xF0001000)
#define UART      		((Uart_Reg*)		0xF0010000)
#define TIMER_PRESCALER ((Prescaler_Reg*)	0xF0020000)
#define VGA_BASE        ((Vga_Reg*)			0xF0030000)
//#define IIC			((I2c_Config*)		0xF0040000)

#define TIMER_INTERRUPT ((InterruptCtrl_Reg*)	0xF0020010)
#define TIMER_A			((Timer_Reg*)			0xF0020040)
#define TIMER_B			((Timer_Reg*)			0xF0020050)
#define SYSTIMER		((SysTimer_Reg*)		0xF0020060)




#endif /* __MURAX_H__ */
