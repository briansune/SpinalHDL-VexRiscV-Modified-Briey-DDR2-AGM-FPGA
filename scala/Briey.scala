package vexriscv.demo

import vexriscv.plugin._
import vexriscv._
import vexriscv.ip.{DataCacheConfig, InstructionCacheConfig}
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.amba4.axi._
import spinal.lib.com.jtag.Jtag
// import spinal.lib.com.jtag.sim.JtagTcp
import spinal.lib.com.uart.sim.{UartDecoder, UartEncoder}
import spinal.lib.com.uart.{
  Apb3UartCtrl,
  Uart,
  UartCtrlGenerics,
  UartCtrlMemoryMappedConfig
}
import spinal.lib.graphic.RgbConfig
import spinal.lib.graphic.vga.{Axi4VgaCtrl, Axi4VgaCtrlGenerics, Vga}
import spinal.lib.io.TriStateArray
import spinal.lib.com.spi.ddr.SpiXdrMaster

import spinal.lib.misc.HexTools
import spinal.lib.soc.pinsec.{PinsecTimerCtrl, PinsecTimerCtrlExternal}
import spinal.lib.system.debugger.{
  JtagAxi4SharedDebugger,
  JtagBridge,
  SystemDebugger,
  SystemDebuggerConfig
}

import scala.collection.mutable.ArrayBuffer
import scala.collection.Seq

// import spinal.lib.blackbox.altera.sld_virtual_jtag
import spinal.lib.misc.InterruptCtrl
import spinal.lib.misc.Prescaler
import spinal.lib.misc.Timer
import spinal.lib.com.i2c.{
  I2cSlaveMemoryMappedGenerics,
  I2cSlaveGenerics,
  I2cMasterMemoryMappedGenerics,
  Apb3I2cCtrl,
  I2c
}
import spinal.lib.com.spi.ddr.{SpiXdrMasterCtrl, SpiXdrParameter}
import spinal.lib.com.spi.ddr.Apb3SpiXdrMasterCtrl
import spinal.lib.bus.simple.PipelinedMemoryBus
import spinal.lib.bus.simple.PipelinedMemoryBusConfig
import spinal.lib.bus.misc.SizeMapping

case class BrieyConfig(
    axiFrequency: HertzNumber,
    onChipRamSize: BigInt,
    //  sdramLayout: SdramLayout,
    //  sdramTimings: SdramTimings,
    cpuPlugins: ArrayBuffer[Plugin[VexRiscv]],
    i2cCtrlConfig: I2cSlaveMemoryMappedGenerics,
    flashConfig: SpiXdrMasterCtrl.MemoryMappingParameters,
    uartCtrlConfig: UartCtrlMemoryMappedConfig,
    hardwareBreakpointCount: Int
) {
  val genBootloader = flashConfig != null
}

object BrieyConfig {
  def default: BrieyConfig = default(false)
  def default(withXip: Boolean = false) = {
    val config = BrieyConfig(
      axiFrequency = 64 MHz,
      onChipRamSize = 16 kB,

      uartCtrlConfig = UartCtrlMemoryMappedConfig(
        uartCtrlConfig = UartCtrlGenerics(
          dataWidthMax = 8,
          clockDividerWidth = 20,
          preSamplingSize = 1,
          samplingSize = 5,
          postSamplingSize = 2
        ),
        txFifoDepth = 16,
        rxFifoDepth = 16
      ),
      i2cCtrlConfig = I2cSlaveMemoryMappedGenerics(
        ctrlGenerics = I2cSlaveGenerics(
          samplingWindowSize = 3,
          samplingClockDividerWidth = 10 bits,
          tsuDataWidth = 6 bits,
          timeoutWidth = 20 bits
        ),
        masterGenerics = I2cMasterMemoryMappedGenerics(
          timerWidth = 20
        )
      ),
      flashConfig = ifGen(withXip)(
        SpiXdrMasterCtrl.MemoryMappingParameters(
          SpiXdrMasterCtrl
            .Parameters(8, 12, SpiXdrParameter(2, 1, 1, 0, 0))
            .addFullDuplex(0, 1, false),
          cmdFifoDepth = 32,
          rspFifoDepth = 32,
          xip = SpiXdrMasterCtrl
            .XipBusParameters(addressWidth = 24, lengthWidth = 10)
        )
      ),
      hardwareBreakpointCount = if (withXip) 3 else 0,
      cpuPlugins = ArrayBuffer(
        new PcManagerSimplePlugin(0x80000000L, false),
        //          new IBusSimplePlugin(
        //            interfaceKeepData = false,
        //            catchAccessFault = true
        //          ),
        new IBusCachedPlugin(
          resetVector = if (withXip) 0xE1000000L else 0x80000000L,
          prediction = STATIC,
          config = InstructionCacheConfig(
            cacheSize = 4096,
            bytePerLine = 32,
            wayCount = 1,
            addressWidth = 32,
            cpuDataWidth = 32,
            memDataWidth = 32,
            catchIllegalAccess = true,
            catchAccessFault = true,
            asyncTagMemory = false,
            twoCycleRam = true,
            twoCycleCache = true
          )
          //            askMemoryTranslation = true,
          //            memoryTranslatorPortConfig = MemoryTranslatorPortConfig(
          //              portTlbSize = 4
          //            )
        ),
        //                    new DBusSimplePlugin(
        //                      catchAddressMisaligned = true,
        //                      catchAccessFault = true
        //                    ),
        new DBusCachedPlugin(
          config = new DataCacheConfig(
            cacheSize = 4096,
            bytePerLine = 32,
            wayCount = 1,
            addressWidth = 32,
            cpuDataWidth = 32,
            memDataWidth = 32,
            catchAccessError = true,
            catchIllegal = true,
            catchUnaligned = true
          ),
          memoryTranslatorPortConfig = null
          //            memoryTranslatorPortConfig = MemoryTranslatorPortConfig(
          //              portTlbSize = 6
          //            )
        ),
        new StaticMemoryTranslatorPlugin(
          ioRange = _(31 downto 28) === 0xf
        ),
        new DecoderSimplePlugin(
          catchIllegalInstruction = true
        ),
        new RegFilePlugin(
          regFileReadyKind = plugin.SYNC,
          zeroBoot = false
        ),
        new IntAluPlugin,
        new SrcPlugin(
          separatedAddSub = false,
          executeInsertion = true
        ),
        new FullBarrelShifterPlugin,
        new MulPlugin,
        new DivPlugin,
        new HazardSimplePlugin(
          bypassExecute = true,
          bypassMemory = true,
          bypassWriteBack = true,
          bypassWriteBackBuffer = true,
          pessimisticUseSrc = false,
          pessimisticWriteRegFile = false,
          pessimisticAddressMatch = false
        ),
        new BranchPlugin(
          earlyBranch = false,
          catchAddressMisaligned = true
        ),
        new CsrPlugin(
          config = CsrPluginConfig(
            catchIllegalAccess = false,
            mvendorid = null,
            marchid = null,
            mimpid = null,
            mhartid = null,
            misaExtensionsInit = 66,
            misaAccess = CsrAccess.NONE,
            mtvecAccess = CsrAccess.NONE,
            // IRQ code location
            mtvecInit = 0x80000020L,
            mepcAccess = CsrAccess.READ_WRITE,
            mscratchGen = false,
            mcauseAccess = CsrAccess.READ_ONLY,
            mbadaddrAccess = CsrAccess.READ_ONLY,
            mcycleAccess = CsrAccess.NONE,
            minstretAccess = CsrAccess.NONE,
            ecallGen = false,
            wfiGenAsWait = false,
            ucycleAccess = CsrAccess.NONE,
            uinstretAccess = CsrAccess.NONE
          )
        ),
        new YamlPlugin("cpu0.yaml")
      )
    )
    config
  }
}

class Briey(val config: BrieyConfig) extends Component {

  // Legacy constructor
  def this(axiFrequency: HertzNumber) {
    this(BrieyConfig.default.copy(axiFrequency = axiFrequency))
  }

  import config._
  val debug = true
  val interruptCount = 4
  def vgaRgbConfig = RgbConfig(5, 6, 5)
  // BANK=3, Row=15, Col=10, 8 bit
  def byteAddressWidth = 3 + 10 + 15 + log2Up(1)
  def capacity = BigInt(1) << byteAddressWidth

  val dramAxiConfig = Axi4Config(
    addressWidth = byteAddressWidth,
    dataWidth = 32,
    idWidth = 4,
    useLock = false,
    useRegion = false,
    useCache = false,
    useProt = false,
    useQos = false
  )

  val io = new Bundle {
    // Clocks / reset
    val asyncReset = in Bool ()
    val axiClk = in Bool ()
    val softReset = out Bool ()

    val vgaClk = in Bool ()
    val vga = master(Vga(vgaRgbConfig))

    // Main components IO
    val jtag = slave(Jtag())
    val dram_axi = master(Axi4Shared(dramAxiConfig))

    // Peripherals IO
    val gpioA = master(TriStateArray(2 bits))
    val gpioB = master(TriStateArray(2 bits))
    val uart = master(Uart())
    // val timerExternal = in(PinsecTimerCtrlExternal())
    val coreInterrupt = in Bool ()
    val i2c = master(I2c())
    val xip = ifGen(genBootloader)(master(SpiXdrMaster(flashConfig.ctrl.spi)))
  }

  val resetCtrlClockDomain = ClockDomain(
    clock = io.axiClk,
    config = ClockDomainConfig(
      resetKind = BOOT
    )
  )

  val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
    val systemResetUnbuffered = False
    //    val coreResetUnbuffered = False

    // Implement an counter to keep the reset axiResetOrder high 64 cycles
    // Also this counter will automaticly do a reset when the system boot.
    val systemResetCounter = Reg(UInt(6 bits)) init (0)
    when(systemResetCounter =/= U(systemResetCounter.range -> true)) {
      systemResetCounter := systemResetCounter + 1
      systemResetUnbuffered := True
    }
    when(BufferCC(io.asyncReset)) {
      systemResetCounter := 0
    }

    // Create all reset used later in the design
    val systemReset = RegNext(systemResetUnbuffered)
    val axiReset = RegNext(systemResetUnbuffered)
    val vgaReset = BufferCC(axiReset)
  }

  val axiClockDomain = ClockDomain(
    clock = io.axiClk,
    reset = resetCtrl.axiReset,
    frequency = FixedFrequency(
      axiFrequency
    ) // The frequency information is used by the SDRAM controller
  )

  val debugClockDomain = ClockDomain(
    clock = io.axiClk,
    reset = resetCtrl.systemReset,
    frequency = FixedFrequency(axiFrequency)
  )

  val vgaClockDomain = ClockDomain(
    clock = io.vgaClk,
    reset = resetCtrl.vgaReset
  )

  val timerInterrupt = Bool()
  val externalInterrupt = Bool()

  val axi = new ClockingArea(axiClockDomain) {

    val ram = Axi4SharedOnChipRam(
      dataWidth = 32,
      byteCount = onChipRamSize,
      idWidth = 4
    )

    val apbBridge = Axi4SharedToApb3Bridge(
      addressWidth = 20,
      dataWidth = 32,
      idWidth = 4
    )

    val gpioACtrl = Apb3Gpio(
      gpioWidth = 2,
      withReadSync = true
    )
    val gpioBCtrl = Apb3Gpio(
      gpioWidth = 2,
      withReadSync = true
    )
    val timerCtrl = new Apb3SysTimer()
    val i2cCtrl = Apb3I2cCtrl(i2cCtrlConfig)
    val uartCtrl = Apb3UartCtrl(uartCtrlConfig)

    uartCtrl.io.apb.addAttribute(Verilator.public)

    val vgaCtrlConfig = Axi4VgaCtrlGenerics(
      axiAddressWidth = 32,
      axiDataWidth = 32,
      burstLength = 64,
      frameSizeMax = 1280 * 720 * 2,
      fifoSize = 1024,
      rgbConfig = vgaRgbConfig,
      vgaClock = vgaClockDomain
    )
    val vgaCtrl = Axi4VgaCtrl(vgaCtrlConfig)

    val core = new Area {
      val config = VexRiscvConfig(
        plugins = cpuPlugins += new DebugPlugin(
          debugClockDomain,
          hardwareBreakpointCount
        )
      )

      timerInterrupt := timerCtrl.io.interrupt
      ifGen(!genBootloader)(externalInterrupt := BufferCC(io.coreInterrupt))

      val cpu = new VexRiscv(config)
      io.softReset := BufferCC(cpu.service(classOf[DebugPlugin]).io.resetOut)
      var iBus: Axi4ReadOnly = null
      var dBus: Axi4Shared = null
      for (plugin <- config.plugins) plugin match {
        case plugin: IBusSimplePlugin => iBus = plugin.iBus.toAxi4ReadOnly()
        case plugin: IBusCachedPlugin => iBus = plugin.iBus.toAxi4ReadOnly()
        case plugin: DBusSimplePlugin => dBus = plugin.dBus.toAxi4Shared()
        case plugin: DBusCachedPlugin => dBus = plugin.dBus.toAxi4Shared(true)
        case plugin: CsrPlugin        => {
          plugin.externalInterrupt := externalInterrupt
          plugin.timerInterrupt := timerInterrupt
        }
        case plugin: DebugPlugin =>
          debugClockDomain {
            resetCtrl.axiReset setWhen (RegNext(plugin.io.resetOut))
            io.jtag <> plugin.io.bus.fromJtag()
          }
        case _ =>
      }
    }

    val apbMapping = ArrayBuffer[(Apb3, SizeMapping)]()
    val axiCrossbar = Axi4CrossbarFactory()
    val core_ibus_list = ArrayBuffer(ram.io.axi, io.dram_axi)
    val core_dbus_list = ArrayBuffer(ram.io.axi, io.dram_axi, apbBridge.io.axi)

    axiCrossbar.addSlaves(
      ram.io.axi -> (0x80000000L, onChipRamSize),
      io.dram_axi -> (0x40000000L, capacity),
      apbBridge.io.axi -> (0xf0000000L, 1 MB)
    )

    val xip = ifGen(genBootloader)(new Area {

      val bootlaoder = Axi4SharedOnChipRam(
        dataWidth = 32,
        byteCount = 64 * 4,
        idWidth = 4
      )
      HexTools.initRam(
        bootlaoder.ram,
        "src/main/c/briey/xipBootloader/crt.hex",
        0xe1000000L
      )

      val ctrl = Apb3SpiXdrMasterCtrl(flashConfig)
      apbMapping += ctrl.io.apb -> (0x1f000, 4 kB)
      ctrl.io.spi <> io.xip

      axiCrossbar.addSlave(bootlaoder.io.axi, (0xe1000000L, 256 Byte))
      core_ibus_list += bootlaoder.io.axi
      core_dbus_list += bootlaoder.io.axi

      axiCrossbar.addPipelining(bootlaoder.io.axi)((crossbar, ctrl) => {
        crossbar.sharedCmd.halfPipe() >> ctrl.sharedCmd
        crossbar.writeData >/-> ctrl.writeData
        crossbar.writeRsp << ctrl.writeRsp
        crossbar.readRsp << ctrl.readRsp
      })

      val xipBus = Axi4Config(
        addressWidth = 24,
        dataWidth = 32,
        idWidth = 4
      )

      val xipAxiS = ctrl.io.xip.fromAxi4Shared(xipBus)
      axiCrossbar.addSlave(xipAxiS, (0xe0000000L, 16 MB))
      core_ibus_list += xipAxiS
      core_dbus_list += xipAxiS

      axiCrossbar.addPipelining(xipAxiS)((crossbar, ctrl) => {
        crossbar.sharedCmd.halfPipe() >> ctrl.sharedCmd
        crossbar.writeData >/-> ctrl.writeData
        crossbar.writeRsp << ctrl.writeRsp
        crossbar.readRsp << ctrl.readRsp
      })

      externalInterrupt := (ctrl.io.interrupt)
    })

    axiCrossbar.addConnections(
      core.iBus -> core_ibus_list.toSeq,
      core.dBus -> core_dbus_list.toSeq,
      vgaCtrl.io.axi -> List(io.dram_axi)
    )

    axiCrossbar.addPipelining(apbBridge.io.axi)((crossbar, bridge) => {
      crossbar.sharedCmd.halfPipe() >> bridge.sharedCmd
      crossbar.writeData.halfPipe() >> bridge.writeData
      crossbar.writeRsp << bridge.writeRsp
      crossbar.readRsp << bridge.readRsp
    })

    axiCrossbar.addPipelining(io.dram_axi)((crossbar, ctrl) => {
      crossbar.sharedCmd.halfPipe() >> ctrl.sharedCmd
      crossbar.writeData >/-> ctrl.writeData
      crossbar.writeRsp << ctrl.writeRsp
      crossbar.readRsp << ctrl.readRsp
    })

    axiCrossbar.addPipelining(ram.io.axi)((crossbar, ctrl) => {
      crossbar.sharedCmd.halfPipe() >> ctrl.sharedCmd
      crossbar.writeData >/-> ctrl.writeData
      crossbar.writeRsp << ctrl.writeRsp
      crossbar.readRsp << ctrl.readRsp
    })

    axiCrossbar.addPipelining(vgaCtrl.io.axi)((ctrl, crossbar) => {
      ctrl.readCmd.halfPipe() >> crossbar.readCmd
      ctrl.readRsp << crossbar.readRsp
    })

    axiCrossbar.addPipelining(core.dBus)((cpu, crossbar) => {
      cpu.sharedCmd >> crossbar.sharedCmd
      cpu.writeData >> crossbar.writeData
      cpu.writeRsp << crossbar.writeRsp
      cpu.readRsp <-< crossbar.readRsp // Data cache directly use read responses without buffering, so pipeline it for FMax
    })

    axiCrossbar.build()

    apbMapping += gpioACtrl.io.apb -> (0x00000, 4 kB)
    apbMapping += gpioBCtrl.io.apb -> (0x01000, 4 kB)
    apbMapping += uartCtrl.io.apb -> (0x10000, 4 kB)
    apbMapping += timerCtrl.io.apb -> (0x20000, 4 kB)
    apbMapping += vgaCtrl.io.apb -> (0x30000, 4 kB)
    apbMapping += i2cCtrl.io.apb -> (0x40000, 4 kB)

    val apbDecoder = Apb3Decoder(
      master = apbBridge.io.apb,
      slaves = apbMapping.toSeq
    )
  }

  io.gpioA <> axi.gpioACtrl.io.gpio
  io.gpioB <> axi.gpioBCtrl.io.gpio
  io.uart <> axi.uartCtrl.io.uart
  io.vga <> axi.vgaCtrl.io.vga
  io.i2c <> axi.i2cCtrl.io.i2c
}

// SoC
object Briey {
  def main(args: Array[String]) {
    val config = SpinalConfig()
    config.generateVerilog({
      val toplevel = new Briey((BrieyConfig.default))
      // toplevel.axi.vgaCtrl.vga.ctrl.io.error.addAttribute(Verilator.public)
      // toplevel.axi.vgaCtrl.vga.ctrl.io.frameStart.addAttribute(Verilator.public)
      toplevel
    })
  }
}

object BrieyWithNorFlash {
  def main(args: Array[String]) {
    val config = SpinalConfig()
    config.generateVerilog({
      val toplevel = new Briey((BrieyConfig.default(true)))
      // toplevel.axi.vgaCtrl.vga.ctrl.io.error.addAttribute(Verilator.public)
      // toplevel.axi.vgaCtrl.vga.ctrl.io.frameStart.addAttribute(Verilator.public)
      toplevel
    })
  }
}

// with memory init
object BrieyWithMemInit {
  def main(args: Array[String]) {
    val config = SpinalConfig()
    config.generateVerilog({
      val toplevel = new Briey(BrieyConfig.default)
      // toplevel.axi.vgaCtrl.vga.ctrl.io.error.addAttribute(Verilator.public)
      // toplevel.axi.vgaCtrl.vga.ctrl.io.frameStart.addAttribute(Verilator.public)
      HexTools.initRam(
        toplevel.axi.ram.ram,
        "src/main/c/briey/hello_world/build/hello_world.hex",
        0x80000000L
      )
      toplevel
    })
  }
}

//DE0-Nano
object BrieyDe0Nano {
  def main(args: Array[String]) {
    val config = SpinalConfig()
    config.generateVerilog({
      val toplevel = new Briey(BrieyConfig.default.copy())
      toplevel
    })
  }
}

import spinal.core.sim._
object BrieySim {
  def main(args: Array[String]): Unit = {
    val simSlowDown = false
    SimConfig.allOptimisation
      .compile(new Briey(BrieyConfig.default))
      .doSimUntilVoid { dut =>
        val mainClkPeriod = (1e12 / dut.config.axiFrequency.toDouble).toLong
        // val jtagClkPeriod = mainClkPeriod*4
        val uartBaudRate = 115200
        val uartBaudPeriod = (1e12 / uartBaudRate).toLong

        val clockDomain = ClockDomain(dut.io.axiClk, dut.io.asyncReset)
        clockDomain.forkStimulus(mainClkPeriod)

        // val tcpJtag = JtagTcp(
        //   jtag = dut.io.jtag,
        //   jtagClkPeriod = jtagClkPeriod
        // )

        val uartTx = UartDecoder(
          uartPin = dut.io.uart.txd,
          baudPeriod = uartBaudPeriod
        )

        val uartRx = UartEncoder(
          uartPin = dut.io.uart.rxd,
          baudPeriod = uartBaudPeriod
        )

        // val sdram = SdramModel(
        // dut.io.sdram,
        // dut.config.sdramLayout,
        // clockDomain
        // )

        // dut.io.coreInterrupt #= false
      }
  }
}
