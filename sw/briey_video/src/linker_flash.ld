OUTPUT_FORMAT("elf32-littleriscv", "elf32-littleriscv", "elf32-littleriscv")
OUTPUT_ARCH(riscv)
ENTRY(crtStart)

MEMORY {
    FLASH (rx) : ORIGIN = 0xE0200000, LENGTH = 2M
    RAM   (rwx): ORIGIN = 0x80000000, LENGTH = 16k
    SDRAM (rwx): ORIGIN = 0x40000000, LENGTH = 256M
}

_stack_size = DEFINED(_stack_size) ? _stack_size : 256;
_heap_size = DEFINED(_heap_size) ? _heap_size : 0;

SECTIONS {
    .boot : {
        . = ALIGN(4);
        *(.boot_entry)
        . = ALIGN(4);
    } > FLASH

    .text : {
        . = ALIGN(4);
        _text_ram_start = .;

		/* Force trap_entry to sit exactly at the 0x20 offset (0x80000020) */
        . = _text_ram_start + 0x20;
        *(.text.trap_entry)

        /* Explicitly reserve the first 32 bytes for initialization routines */
        *(.text.startup_ram)
        
        *(.text .text.*)
        *(.rodata .rodata.*)
        . = ALIGN(4);
        _text_ram_end = .;
    } > RAM AT > FLASH

    _text_flash_start = LOADADDR(.text);

    .data : {
        . = ALIGN(4);
        _data_ram_start = .;
        *(.data .data.*)
        . = ALIGN(8);
        PROVIDE( __global_pointer$ = . + 0x800 );
        *(.sdata .sdata.*)
        . = ALIGN(4);
        _data_ram_end = .;
    } > RAM AT > FLASH

    _data_flash_start = LOADADDR(.data);

    .bss : {
        . = ALIGN(4);
        _bss_start = .;
        *(.sbss .sbss.*)
        *(.bss .bss.*)
        *(COMMON)
        . = ALIGN(4);
        _bss_end = .;
    } > RAM

	  ._user_heap (NOLOAD):
	  {
	    . = ALIGN(8);
	    PROVIDE ( end = . );
	    PROVIDE ( _end = . );
	    PROVIDE ( _heap_start = .);
	    . = . + _heap_size;
	    . = ALIGN(8);
	    PROVIDE ( _heap_end = .);
	  } > RAM

    .stack (NOLOAD) : {
        . = ALIGN(16);
        _stack_end = .;
        . = . + _stack_size;
        . = ALIGN(16);
        _stack_start = .;
    } > RAM

  .noinit (NOLOAD) :
  {
      . = ALIGN(4);
      *(.noinit .noinit.*)
      . = ALIGN(4);
  } > SDRAM

    .ctors : {
        . = ALIGN(4);
        _ctors_start = .;
        KEEP(*(.init_array*))
        KEEP (*(SORT(.ctors.*)))
        KEEP (*(.ctors))
        . = ALIGN(4);
        _ctors_end = .;
        PROVIDE ( END_OF_SW_IMAGE = . );
    } > RAM
}