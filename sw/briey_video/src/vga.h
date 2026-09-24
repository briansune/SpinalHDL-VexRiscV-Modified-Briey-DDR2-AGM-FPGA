/*
 * vga.h
 *
 *  Created on: Jul 8, 2017
 *      Author: spinalvm
 */

#ifndef VGA_H_
#define VGA_H_
#include <stdint.h>

typedef struct {
	uint32_t hSyncStart ,hSyncEnd;
	uint32_t hColorStart,hColorEnd;

	uint32_t vSyncStart ,vSyncEnd;
	uint32_t vColorStart,vColorEnd;
}Vga_Timing;


const static Vga_Timing vga_h1280_v720 = {
	.hSyncStart  = 32 - 1,				// hSync - 1
	.hSyncEnd    = 1440 - 1,			// hSync + hBack + hPixels + hFront - 1
	.hColorStart = 32 + 80 - 1,			// hSync + hBack - 1
	.hColorEnd   = 32 + 80 + 1280 - 1,	// hSync + hBack + hPixels - 1
	.vSyncStart  = 5 - 1,
	.vSyncEnd 	 = 741 - 1,
	.vColorStart = 5 + 13 - 1,
	.vColorEnd 	 = 5 + 13 + 720 - 1
};

typedef struct
{
  volatile uint32_t  STATUS;
  volatile uint32_t  FRAME_SIZE;
  volatile uint32_t  FRAME_BASE;
  volatile uint32_t  DUMMY0[13];
  volatile Vga_Timing TIMING;
} Vga_Reg;

static uint32_t vga_isBusy(Vga_Reg *reg){
	return (reg->STATUS & 2) != 0;
}

static void vga_run(Vga_Reg *reg){
	reg->STATUS  = 1;
}

static void vga_stop(Vga_Reg *reg){
	reg->STATUS  = 0;
	while(vga_isBusy(reg));
}


#endif /* VGA_H_ */


