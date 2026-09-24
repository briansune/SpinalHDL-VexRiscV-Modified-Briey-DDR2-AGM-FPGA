#define dataFlush(_data) \
r_type_insn(0b0111000, 0, regnum_ ## _data, 0b101, 0, 0b0001111)

#define regnum_zero 0
#define regnum_ra   1
#define regnum_sp   2
#define regnum_gp   3
#define regnum_tp   4
#define regnum_t0   5
#define regnum_t1   6
#define regnum_t2   7
#define regnum_s0   8
#define regnum_s1   9
#define regnum_a0  10
#define regnum_a1  11
#define regnum_a2  12
#define regnum_a3  13
#define regnum_a4  14
#define regnum_a5  15
#define regnum_a6  16
#define regnum_a7  17
#define regnum_s2  18
#define regnum_s3  19
#define regnum_s4  20
#define regnum_s5  21
#define regnum_s6  22
#define regnum_s7  23
#define regnum_s8  24
#define regnum_s9  25
#define regnum_s10 26
#define regnum_s11 27
#define regnum_t3  28
#define regnum_t4  29
#define regnum_t5  30
#define regnum_t6  31

#define r_type_insn(_f7, _rs2, _rs1, _f3, _rd, _opc) \
.word (((_f7) << 25) | ((_rs2) << 20) | ((_rs1) << 15) | ((_f3) << 12) | ((_rd) << 7) | ((_opc) << 0))

.global irqCallback
.global main
.global crtStart
.global trap_entry
.global crtInit

.section .boot_entry, "ax",@progbits

crtStart:
    # ----------------------------------------------------
    # 1. Copy the Application Code (.text) from Flash to RAM
    # ----------------------------------------------------
    la  t0, _text_flash_start   # Source address in Flash
    la  t1, _text_ram_start     # Destination address in RAM
    la  t2, _text_ram_end       # End marker in RAM

copy_text_loop:
    bge t1, t2, end_copy_text
    lw  t3, 0(t0)               # Read word from Flash
    sw  t3, 0(t1)               # Write word to RAM
    addi t0, t0, 4
    addi t1, t1, 4
    j   copy_text_loop
end_copy_text:

    # ----------------------------------------------------
    # 2. Copy Global Variables (.data) from Flash to RAM
    # ----------------------------------------------------
    la  t0, _data_flash_start
    la  t1, _data_ram_start
    la  t2, _data_ram_end

copy_data_loop:
    bge t1, t2, end_copy_data
    lw  t3, 0(t0)
    sw  t3, 0(t1)
    addi t0, t0, 4
    addi t1, t1, 4
    j   copy_data_loop
end_copy_data:

    # ----------------------------------------------------
    # 3. Clear the BSS Memory Space in RAM
    # ----------------------------------------------------
    la  t1, _bss_start
    la  t2, _bss_end
clear_bss_loop:
    bge t1, t2, end_clear_bss
    sw  zero, 0(t1)
    addi t1, t1, 4
    j   clear_bss_loop
end_clear_bss:

    # ----------------------------------------------------
    # 4. Absolutely Crucial: Jump to the RAM Instance!
    # ----------------------------------------------------
  //long jump to allow crtInit to be anywhere
  //do it always in 12 bytes
  lui x2,       %hi(crtInit)
  addi x2, x2,  %lo(crtInit)
  jalr x1,x2
  nop


# ---------------------------------------------------------------------
# FIXED: Lands directly on 0x80000020 for CsrPluginConfig.smallest
# ---------------------------------------------------------------------
.section .text.trap_entry, "ax"
.align 4
trap_entry:
    # Safe Stack Move down FIRST before storing variable maps
    addi sp, sp, -16*4

    sw x1,   0*4(sp)
    sw x5,   1*4(sp)
    sw x6,   2*4(sp)
    sw x7,   3*4(sp)
    sw x10,  4*4(sp)
    sw x11,  5*4(sp)
    sw x12,  6*4(sp)
    sw x13,  7*4(sp)
    sw x14,  8*4(sp)
    sw x15,  9*4(sp)
    sw x16, 10*4(sp)
    sw x17, 11*4(sp)
    sw x28, 12*4(sp)
    sw x29, 13*4(sp)
    sw x30, 14*4(sp)
    sw x31, 15*4(sp)

    call irqCallback

    lw x1,   0*4(sp)
    lw x5,   1*4(sp)
    lw x6,   2*4(sp)
    lw x7,   3*4(sp)
    lw x10,  4*4(sp)
    lw x11,  5*4(sp)
    lw x12,  6*4(sp)
    lw x13,  7*4(sp)
    lw x14,  8*4(sp)
    lw x15,  9*4(sp)
    lw x16, 10*4(sp)
    lw x17, 11*4(sp)
    lw x28, 12*4(sp)
    lw x29, 13*4(sp)
    lw x30, 14*4(sp)
    lw x31, 15*4(sp)

    addi sp, sp, 16*4
    mret


# ---------------------------------------------------------------------
# This segment maps below the 0x20 marker to preserve correct offset room
# ---------------------------------------------------------------------
.section .text.startup_ram, "ax"

crtInit:
  .option push
  .option norelax
  la gp, __global_pointer$
  .option pop
  la sp, _stack_start

bss_init:
  la a0, _bss_start
  la a1, _bss_end
bss_loop:
  beq a0,a1,bss_done
  sw zero,0(a0)
  add a0,a0,4
  j bss_loop
bss_done:

ctors_init:
  la a0, _ctors_start
  addi sp,sp,-4
ctors_loop:
  la a1, _ctors_end
  beq a0,a1,ctors_done
  lw a3,0(a0)
  add a0,a0,4
  sw a0,0(sp)
  jalr  a3
  lw a0,0(sp)
  j ctors_loop
ctors_done:
  addi sp,sp,4


  li a0, 0x880     //880 enable timer + external interrupts
  csrw mie,a0
  li a0, 0x1808     //1808 enable interrupts
  csrw mstatus,a0

  call main
infinitLoop:
  j infinitLoop

.section .text
.global flushDataCache
.type flushDataCache, @function
flushDataCache:
  li a0, 4096
flushDataCacheLoop:
  ADDI a0,a0,-32
  dataFlush(a0)
  bnez a0, flushDataCacheLoop
  ret

