PROJ_NAME=briey_video
DEBUG=no
BENCH=no
MULDIV=no

SRCS = 	$(wildcard src/*.c) \
		$(wildcard src/*.cpp)
#		$(wildcard src/*.S)

SRC_ASM = src/crt.S
SRC_ASM_NOR = src/crt_nor.S
SRC_ASM_SD  = src/crt_sd.S

OBJDIR = build

INC  =
LIBS =
LIBSINC = -L$(OBJDIR)
LDSCRIPT = ./src/linker.ld
LDSCRIPT_NOR = ./src/linker_flash.ld
LDSCRIPT_SD = ./src/linker_flash_sd.ld

#include ../../../resources/gcc.mk
# Set it to yes if you are using the sifive precompiled GCC pack
#SIFIVE_GCC_PACK ?= yes

ifeq ($(SIFIVE_GCC_PACK),yes)
	RISCV_NAME ?= riscv64-unknown-elf
#	RISCV_PATH ?= /opt/riscv/
	RISCV_PATH ?= /ucrt64/
else
	RISCV_NAME ?= riscv32-unknown-elf
	ifeq ($(MULDIV),yes)
		RISCV_PATH ?= /opt/riscv32im/
	else
		RISCV_PATH ?= /ucrt64/
	endif
endif

MABI=ilp32
MARCH := rv32i_zicsr
ifeq ($(MULDIV),yes)
	MARCH := $(MARCH)m
endif
ifeq ($(COMPRESSED),yes)
	MARCH := $(MARCH)ac
endif

CFLAGS += -march=$(MARCH)  -mabi=$(MABI) -DNDEBUG
LDFLAGS += -march=$(MARCH)  -mabi=$(MABI)



#include ../../../resources/subproject.mk


ifeq ($(DEBUG),yes)
	CFLAGS += -g3 -O0
endif

ifeq ($(DEBUG),no)
	CFLAGS += -g -Os
endif

ifeq ($(BENCH),yes)
	CFLAGS += -fno-inline
endif

ifeq ($(SIFIVE_GCC_PACK),yes)
	RISCV_CLIB=$(RISCV_PATH)/$(RISCV_NAME)/lib/$(MARCH)/$(MABI)/
else
	RISCV_CLIB=$(RISCV_PATH)/$(RISCV_NAME)/lib/
endif





RISCV_OBJCOPY = $(RISCV_PATH)/bin/$(RISCV_NAME)-objcopy
RISCV_OBJDUMP = $(RISCV_PATH)/bin/$(RISCV_NAME)-objdump
RISCV_CC=$(RISCV_PATH)/bin/$(RISCV_NAME)-gcc

CFLAGS +=  -MD -fstrict-volatile-bitfields -fno-strict-aliasing

LDFLAGS +=  -nostdlib -lgcc -lc -mcmodel=medany -nostartfiles -ffreestanding -Wl,-Bstatic,-T,$(LDSCRIPT),-Map,$(OBJDIR)/$(PROJ_NAME).map,--print-memory-usage
LDFLAGS_NOR +=  -nostdlib -lgcc -lc -mcmodel=medany -nostartfiles -ffreestanding -Wl,-Bstatic,-T,$(LDSCRIPT_NOR),-Map,$(OBJDIR)/$(PROJ_NAME)_nor.map,--print-memory-usage
LDFLAGS_SD +=  -nostdlib -lgcc -lc -mcmodel=medany -nostartfiles -ffreestanding -Wl,-Bstatic,-T,$(LDSCRIPT_SD),-Map,$(OBJDIR)/$(PROJ_NAME)_sd.map,--print-memory-usage

#LDFLAGS += -lgcc -lc -lg -nostdlib  -lgcc  -msave-restore    --strip-debug,

OBJS := $(SRCS)
OBJS := $(OBJS:.c=.o)
OBJS := $(OBJS:.cpp=.o)

#OBJS := $(OBJS:.S=.o)
OBJ_RAM = $(OBJDIR)/$(SRC_ASM:.S=.o)
OBJ_NOR = $(OBJDIR)/$(SRC_ASM_NOR:.S=.o)
OBJ_SD  = $(OBJDIR)/$(SRC_ASM_SD:.S=.o)

OBJS := $(OBJS:..=miaou)
OBJS := $(addprefix $(OBJDIR)/,$(OBJS))

# Define your base output targets
TARGETS_BASE = $(PROJ_NAME) $(PROJ_NAME)_nor $(PROJ_NAME)_sd

# Create the full list of files needed for 'all'
ALL_TARGETS = $(foreach t, $(TARGETS_BASE), \
    $(OBJDIR)/$(t).elf $(OBJDIR)/$(t).bin $(OBJDIR)/$(t).asm $(OBJDIR)/$(t).v)

ALL_TARGETS += $(foreach t, $(TARGETS_BASE), \
	$(OBJDIR)/$(t).hex)

all: $(ALL_TARGETS)

$(OBJDIR)/$(PROJ_NAME).elf: $(OBJS) $(OBJ_RAM) | $(OBJDIR)
	$(RISCV_CC) $(CFLAGS) -o $@ $^ $(LDFLAGS) $(LIBSINC) $(LIBS)

$(OBJDIR)/$(PROJ_NAME)_nor.elf: $(OBJS) $(OBJ_NOR) | $(OBJDIR)	
	$(RISCV_CC) $(CFLAGS) -o $@ $^ $(LDFLAGS_NOR) $(LIBSINC) $(LIBS)

$(OBJDIR)/$(PROJ_NAME)_sd.elf: $(OBJS) $(OBJ_SD) | $(OBJDIR)
	$(RISCV_CC) $(CFLAGS) -o $@ $^ $(LDFLAGS_SD) $(LIBSINC) $(LIBS)

$(OBJDIR)/$(PROJ_NAME).hex: $(OBJDIR)/$(PROJ_NAME).elf
	$(RISCV_OBJCOPY) -O ihex $^ $@

$(OBJDIR)/$(PROJ_NAME)_sd.hex: $(OBJDIR)/$(PROJ_NAME).elf
	$(RISCV_OBJCOPY) -O ihex $^ $@

$(OBJDIR)/$(PROJ_NAME)_nor.hex: $(OBJDIR)/$(PROJ_NAME)_nor.elf
	$(RISCV_OBJCOPY) -O ihex --change-addresses -0xE0000000 $^ $@

%.bin: %.elf
	$(RISCV_OBJCOPY) -O binary $^ $@

%.v: %.elf
	$(RISCV_OBJCOPY) -O verilog $^ $@

%.asm: %.elf
	$(RISCV_OBJDUMP) -S -d $^ > $@

$(OBJDIR)/%.o: %.c
	mkdir -p $(dir $@)
	$(RISCV_CC) -c $(CFLAGS)  $(INC) -o $@ $^
	$(RISCV_CC) -S $(CFLAGS)  $(INC) -o $@.disasm $^

$(OBJDIR)/%.o: %.cpp
	mkdir -p $(dir $@)
	$(RISCV_CC) -c $(CFLAGS)  $(INC) -o $@ $^

$(OBJDIR)/%.o: %.S
	mkdir -p $(dir $@)
	$(RISCV_CC) -c $(CFLAGS) -o $@ $^ -D__ASSEMBLY__=1

$(OBJDIR):
	mkdir -p $@

.PHONY: clean
clean:
	rm -rf $(OBJDIR)/src
	rm -f $(OBJDIR)/$(PROJ_NAME)*.elf
	rm -f $(OBJDIR)/$(PROJ_NAME)*.bin
	rm -f $(OBJDIR)/$(PROJ_NAME)*.hex
	rm -f $(OBJDIR)/$(PROJ_NAME)*.map
	rm -f $(OBJDIR)/$(PROJ_NAME)*.v
	rm -f $(OBJDIR)/$(PROJ_NAME)*.asm
	find $(OBJDIR) -type f -name '*.o' -print0 | xargs -0 -r rm
	find $(OBJDIR) -type f -name '*.d' -print0 | xargs -0 -r rm

clean-all : clean 

.SECONDARY: $(OBJS)
