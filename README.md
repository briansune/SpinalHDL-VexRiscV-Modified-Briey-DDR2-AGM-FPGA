# Modified Briey Example with DDR2 Memory and HDMI Video

## Introduction

Original Briey with VexRISCV project uses scala based general SDRAM controller design and demonstrated a low video resoultion.

With the use of blackbox exporting, shared-AXI4 bus IO can easily migrated with any DDR2 vendor or custom controller.

In this example a custom DDR2 is used, this modified and reconstructed Xilinx ISE old unencrypted Vritex 3/4 DDR design has not use any DQS fabric.

Without DQS fabric many general FPGA with SSTL-1V8 VREF can be applied and real system shows 166MHz on Cyclone 10LP and 133 on Cyclone IV E.

Due to AGM AG16KF256 FPGA routing and placement slacks and hold timing is very unreliable.

Real hardware can only achieve 128MHz and same Pin-to-Pin C4 with 133MHz thermal also shows AGM heats up much more than expected.

## Resource

In the project, scala VexRISCV is shared with related C coding.

User can run it easily via Eclipse IDE

The DDR2 controller and bridge are not shared yet

## FPGA (AGM) LE Usages

### JTAG BRAM Variant + (AGM remapping)
```
Family	Cyclone IV E
Device	EP4CE15F17C8
Timing Models	Final
Total logic elements	7,759 / 15,408 ( 50 % )
Total registers	4630
Total pins	58 / 166 ( 35 % )
Total virtual pins	0
Total memory bits	289,536 / 516,096 ( 56 % )
Embedded Multiplier 9-bit elements	8 / 112 ( 7 % )
Total PLLs	2 / 4 ( 50 % )

Placement Statistics
 Total  Logic    Counts  : 7759/16624 (46.7%)
 Total  Logic    Tiles   : 605/1039 (58.2%)
 Total  Other    Tiles   : 46/112 (41.1%)
 Total  Valid    Nets    : 11392 (5598+5794)
 Total  Valid    Fanouts : 48811 (25615+23196)
 Total  Tile     Fanouts : 11424
 Tile   Zip      Fanins  : 14 (0:56)
 Tile   Zip      Fanouts : 24 (1:331)
 Total  Ignored  Nets    : 5744
 Total  Valid    Blocks  : 666 (628/36)
 Total  Ignored  Blocks  : 0
 Total  Zip Complexities : 4602/16213 1.80/8268.97
 Avg    Zip   Bottleneck : 5.87 62.65
 Avg    Net   Bottleneck : 17.31 530.57
```

### NOR-Flash Variant + (AGM remapping)
```
Family	Cyclone IV E
Device	EP4CE15F17C8
Timing Models	Final
Total logic elements	8,430 / 15,408 ( 55 % )
Total registers	5044
Total pins	66 / 166 ( 40 % )
Total virtual pins	0
Total memory bits	294,240 / 516,096 ( 57 % )
Embedded Multiplier 9-bit elements	8 / 112 ( 7 % )
Total PLLs	2 / 4 ( 50 % )

Placement Statistics
 Total  Logic    Counts  : 8430/16624 (50.7%)
 Total  Logic    Tiles   : 650/1039 (62.6%)
 Total  Other    Tiles   : 52/112 (46.4%)
 Total  Valid    Nets    : 12286 (6115+6171)
 Total  Valid    Fanouts : 52900 (27893+25007)
 Total  Tile     Fanouts : 12520
 Tile   Zip      Fanins  : 15 (0:56)
 Tile   Zip      Fanouts : 25 (0:376)
 Total  Ignored  Nets    : 6265
 Total  Valid    Blocks  : 719 (679/38)
 Total  Ignored  Blocks  : 0
 Total  Zip Complexities : 4976/17762 1.80/8958.58
 Avg    Zip   Bottleneck : 5.83 62.24
 Avg    Net   Bottleneck : 17.20 514.73
```

## Result

<img src="image/board.JPG" width="400" />

### Default SpinalHDL Video Controller

| Test Case | Image |
|-|-|
| R | <img src="image/720p_tile_red.JPG" width="400" /> |
| G | <img src="image/720p_tile_green.JPG" width="400" /> |
| B | <img src="image/720p_tile_blue.JPG" width="400" /> |
