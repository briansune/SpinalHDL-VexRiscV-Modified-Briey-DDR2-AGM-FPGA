#define CTRL_BASE 0xF001F000
#define XIP_BASE  0xE0200000
#define CTRL_DATA 0x00
#define CTRL_STATUS 0x04
#define CTRL_MODE 0x08
#define CTRL_RATE 0x20
#define CTRL_SS_SETUP 0x24
#define CTRL_SS_HOLD 0x28
#define CTRL_SS_DISABLE 0x2C

#define CTRL_XIP_CONFIG 0x40
#define CTRL_XIP_MODE 0x44

#define SD_START_SECTOR 0x800
#define APP_RAM_BASE    0x80000000

.global crtStart
.global main

#define CTRL x31

crtStart:
  li x31, CTRL_BASE
  # CPHA=0, CPOL=0
  sw x0, CTRL_MODE(CTRL)
  # start from 400-250kHz for SDMMC CFG
  li t0, 125
  sw t0, CTRL_RATE(CTRL)
  li t0, 4
  sw t0, CTRL_SS_SETUP(CTRL)
  sw t0, CTRL_SS_HOLD(CTRL)
  sw t0, CTRL_SS_DISABLE(CTRL)

	li a0, 0x880
	call spiWrite
	li t1, 10
clk_loop:
    li a0, 0x1FF
	call spiWrite
    addi t1, t1, -1
    bnez t1, clk_loop

sd_init_retry:
    # Send CMD0 (Software Reset to enter SPI mode)
    li a0, 0x140
    call spiWrite
    li a0, 0x100
    call spiWrite
    call spiWrite
    call spiWrite
    call spiWrite
    li a0, 0x195
    call spiWrite

	# Ncr
	li a0, 0x1FF
    call spiWrite

	# Wait for R1 Response (Looking for 0x01)
    call spiRead
    li t0, 0x01
    bne a0, t0, sd_init_retry

    # Dummy
	li a0, 0x1FF
    call spiWrite
    call spiWrite

	# Send CMD8 (Check Voltage Range)
    li a0, 0x148
    call spiWrite
    li a0, 0x100
    call spiWrite
    call spiWrite
    li a0, 0x101
    call spiWrite
    li a0, 0x1AA
    call spiWrite
    li a0, 0x187
    call spiWrite

    # Ncr
	li a0, 0x1FF
    call spiWrite

	# Read CMD8 response
    call spiRead # expect 0x01
    call spiRead # expect 0x00
    call spiRead # expect 0x00
    call spiRead # expect 0x01
    call spiRead # expect 0xAA

acmd41_loop:

	# Dummy
	li a0, 0x1FF
    call spiWrite
    call spiWrite

    # Send CMD55 (App-Specific Command prefix)
    li a0, 0x177
    call spiWrite
    li a0, 0x100
    call spiWrite
    call spiWrite
    call spiWrite
    call spiWrite
    li a0, 0x1FF
    call spiWrite

    # Ncr
	li a0, 0x1FF
    call spiWrite
    call spiRead

    # Dummy
	li a0, 0x1FF
    call spiWrite
    call spiWrite

    # Send ACMD41 (Activate Card Initialization with HCS bit set)
    li a0, 0x169
    call spiWrite
    li a0, 0x140
    call spiWrite
    li a0, 0x100
    call spiWrite
    call spiWrite
    call spiWrite
    li a0, 0x1FF
    call spiWrite

    # Ncr
	li a0, 0x1FF
    call spiWrite
    call spiRead

    # Check if card is ready (0x00)
    li t0, 0x00
    bne a0, t0, acmd41_loop

	# Dummy
	li a0, 0x1FF
    call spiWrite
    call spiWrite

    # 1. Send CMD58 Command Frame
    li a0, 0x17A            # 0x40 + 58 = 0x4A (or 0x17A if using bit 8 indicator)
    call spiWrite
    li a0, 0x100            # Argument Byte 1 (0x00)
    call spiWrite
    call spiWrite           # Argument Byte 2 (0x00)
    call spiWrite           # Argument Byte 3 (0x00)
    call spiWrite           # Argument Byte 4 (0x00)
    li a0, 0x1FF
    call spiWrite

    # 2. Poll for the R1 Token (Byte 0 of R3)
    li a0, 0x1FF
    call spiWrite
    call spiRead
    mv s1, a0

	# Read OCR Byte 3
    call spiRead			# OCR[31:24]
    # Read OCR Byte 2
    call spiRead			# OCR[23:16]
    # Read OCR Byte 1
	call spiRead			# OCR[15:8]
    # Read OCR Byte 0
    call spiRead			# OCR[7:0]

	# Dummy
	li a0, 0x1FF
    call spiWrite
    call spiWrite

    # 1. Send CMD16 Command Frame
    li a0, 0x150            # 0x40 + 16 = 0x50
    call spiWrite
    li a0, 0x100            # Argument Byte 1 (0x00)
    call spiWrite
    call spiWrite           # Argument Byte 2 (0x00)
    li a0, 0x102
    call spiWrite           # Argument Byte 3 (0x02)
    li a0, 0x100
    call spiWrite           # Argument Byte 4 (0x00)
    li a0, 0x1FF
    call spiWrite

    li a0, 0x1FF
    call spiWrite
    call spiRead

	# Dummy
    call spiRead
    call spiRead

    li a0, 0x800
	call spiWrite

    # -----------------------------------------------------------------
    # CARD INITIALIZED! Now shift to high gear for rapid execution
    # Set fast clock for Data Transfer Phase (~25 MHz)
    # 100 MHz / 4 = 25 MHz
    # -----------------------------------------------------------------
    li t0, 4
    sw t0, CTRL_RATE(CTRL)

	li s3, SD_START_SECTOR
	li s4, APP_RAM_BASE

read_blocks:
    li a0, 0x880
	call spiWrite

	# Dummy
	li a0, 0x1FF
    call spiWrite
    call spiWrite

	# CMD18
	li a0, 0x152
    call spiWrite
    srli a0, s3, 24
    andi a0, a0, 0xFF
    ori a0, a0, 0x100
    call spiWrite
    srli a0, s3, 16
    andi a0, a0, 0xFF
    ori a0, a0, 0x100
    call spiWrite
    srli a0, s3, 8
    andi a0, a0, 0xFF
    ori a0, a0, 0x100
    call spiWrite
    andi a0, s3, 0xFF
    ori a0, a0, 0x100
    call spiWrite
    li a0, 0x1FF
    call spiWrite

    li a0, 0x1FF
    call spiWrite
wait_r1_loop:
    call spiRead
    bnez a0, wait_r1_loop

wait_token_loop:
	call spiRead
	li t0, 0xFE
	bne a0, t0, wait_token_loop

	li t1, 512
stream_block_loop:
	call spiRead
	sb a0, 0(s4)
	addi s4, s4, 1
    addi t1, t1, -1
	bnez t1, stream_block_loop

	# Clear 2-byte block CRC
    call spiRead
    call spiRead

    # PARSE METHOD 3 HEADER VALUES NOW SITTING AT RAM 0x80000000
    li   t2, APP_RAM_BASE
    lw   t0, 4(t2)              # Load byte offset 4 -> Magic Signature
    li   t1, 0x53444254         # "SDBT" magic constant
    bne  t0, t1, boot_panic_trap

    lw   s2, 12(t2)             # Load byte offset 12 -> _app_size_sectors count
    addi s2, s2, -1             # Subtract 1 since we just read the header block

stream_payload_sectors:
    beqz s2, streaming_complete

wait_token_payload:
    call spiRead
    li t0, 0xFE
    bne a0, t0, wait_token_payload

    li t1, 512
stream_data_loop:
    call spiRead
    sb   a0, 0(s4)              # Stream directly into progressive RAM addresses
    addi s4, s4, 1
    addi t1, t1, -1
    bnez t1, stream_data_loop

    # Clear 2-byte block CRC
    call spiRead
    call spiRead

    addi s2, s2, -1             # Decrement sector sequence counter
    j    stream_payload_sectors

streaming_complete:

	# Dummy
	li a0, 0x1FF
    call spiWrite
    call spiWrite

    # CMD12
    li   a0, 0x14C
    call spiWrite
    li   a0, 0x100
    call spiWrite
    call spiWrite
    call spiWrite
    call spiWrite
    li   a0, 0x1FF
    call spiWrite

    li a0, 0x1FF
    call spiWrite
    call spiRead

	# Dummy
    call spiRead
    call spiRead

    li a0, 0x800
	call spiWrite

	.option push
    .option arch, +zifencei
    fence.i
    .option pop
    li   t0, APP_RAM_BASE
    jr   t0

boot_panic_trap:
	ebreak
    j    boot_panic_trap


spiWrite:
  lw	t0,CTRL_STATUS(CTRL)
  slli	t0, t0, 0x10
  beqz	t0, spiWrite
  sw    a0, CTRL_DATA(CTRL)
  ret

spiRead:
    lw   t0, CTRL_STATUS(CTRL)
    slli t0, t0, 0x10
    beqz t0, spiRead
    li   a0, 0x200
    sw   a0, CTRL_DATA(CTRL)
spiByte_wait:
	lw   t0, CTRL_STATUS(CTRL)
    srli t0, t0, 0x10
    beqz t0, spiByte_wait
    lw   a0, CTRL_DATA(CTRL)
    ret

