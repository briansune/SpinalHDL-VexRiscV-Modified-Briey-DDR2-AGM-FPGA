#define CTRL_BASE 0xF001F000

#define CTRL_DATA 0x00
#define CTRL_STATUS 0x04
#define CTRL_MODE 0x08
#define CTRL_RATE 0x20
#define CTRL_SS_SETUP 0x24
#define CTRL_SS_HOLD 0x28
#define CTRL_SS_DISABLE 0x2C

#define COPY_WORDS 4096


.global crtStart
.global main

#define CTRL x31

crtStart:
  li x31, CTRL_BASE
  sw x0, CTRL_MODE(CTRL)
  li t0, 2
  sw t0, CTRL_RATE(CTRL)
  li t0, 4
  sw t0, CTRL_SS_SETUP(CTRL)
  sw t0, CTRL_SS_HOLD(CTRL)
  sw t0, CTRL_SS_DISABLE(CTRL)


  li a0, 0x880
  call spiWrite
  li a0, 0x181
  call spiWrite
  li a0, 0x183
  call spiWrite
  li a0, 0x800
  call spiWrite

	li a0, 0x880
	call spiWrite

    # Send Fast Read Opcode (0x0B)
    li   a0, 0x10B
    call spiWrite

    # Send 24-bit Flash Address (0x200000)
    li   a0, 0x120
    call spiWrite
    li   a0, 0x100
    call spiWrite
    li   a0, 0x100
    call spiWrite

	li   a0, 0x100
    call spiWrite

	lui  s1, 0x80000
    li   s2, COPY_WORDS

copy_word_loop:
    # Read 4 bytes from SPI Flash to construct one 32-bit word
    call spiRead
    mv   t1, a0

    call spiRead
    slli t2, a0, 8
    or   t1, t1, t2

    call spiRead
    slli t2, a0, 16
    or   t1, t1, t2

    call spiRead
    slli t2, a0, 24
    or   t1, t1, t2

    # Store 32-bit word into OCRAM
    sw   t1, 0(s1)

    addi s1, s1, 4
    addi s2, s2, -1
    bnez s2, copy_word_loop

	li a0, 0x800
	call spiWrite

	fence.i

	# Set Stack Pointer to top of RAM (e.g., top of 16KB RAM at 0x80008000)
    lui  sp, 0x80004

    # Jump to Application Entry Point in OCRAM
    lui  t0, 0x80000
    jr   t0


spiWrite:
  sw    a0,CTRL_DATA(CTRL)
spiWrite_wait:
  lw	t0,CTRL_STATUS(CTRL)
  slli t0,t0,0x10
  beqz	t0,spiWrite_wait
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
