package vexriscv.demo

import java.nio.{ByteBuffer, ByteOrder}

import spinal.core._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3Config, Apb3SlaveFactory}
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.misc.{HexTools, InterruptCtrl, Prescaler, Timer}
import spinal.lib._
import spinal.lib.bus.simple._
import vexriscv.plugin.{DBusSimpleBus, IBusSimpleBus}
import spinal.lib.com.spi.ddr._
import spinal.lib.bus.bmb._
import spinal.lib.io.InOutWrapper

class Apb3SysTimer extends Component {
  val io = new Bundle {
    val apb = slave(
      Apb3(
        addressWidth = 8,
        dataWidth = 32
      )
    )
    val interrupt = out Bool ()
  }

  val prescaler = Prescaler(16)
  val timerA, timerB = Timer(16)

  // A 64-bit uptime counter that increments every single clock cycle
  val timerValue = Reg(UInt(64 bits)) init (0)
  timerValue := timerValue + 1

  val busCtrl = Apb3SlaveFactory(io.apb)
  val prescalerBridge = prescaler.driveFrom(busCtrl, 0x00)

  val timerABridge = timerA.driveFrom(busCtrl, 0x40)(
    ticks = List(True, prescaler.io.overflow),
    clears = List(timerA.io.full)
  )

  val timerBBridge = timerB.driveFrom(busCtrl, 0x50)(
    ticks = List(True, prescaler.io.overflow),
    clears = List(timerB.io.full)
  )

  busCtrl.read(timerValue(31 downto 0), 0x60)
  busCtrl.read(timerValue(63 downto 32), 0x64)

  val interruptCtrl = InterruptCtrl(2)
  val interruptCtrlBridge = interruptCtrl.driveFrom(busCtrl, 0x10)
  interruptCtrl.io.inputs(0) := timerA.io.full
  interruptCtrl.io.inputs(1) := timerB.io.full
  io.interrupt := interruptCtrl.io.pendings.orR
}

case class Apb3XipRom(onChipRamBinFile : String) extends Component{
  import java.nio.file.{Files, Paths}
  val byteArray = Files.readAllBytes(Paths.get(onChipRamBinFile))
  val wordCount = (byteArray.length+3)/4
  val buffer = ByteBuffer.wrap(Files.readAllBytes(Paths.get(onChipRamBinFile))).order(ByteOrder.LITTLE_ENDIAN);
  val wordArray = (0 until wordCount).map(i => {
    val v = buffer.getInt
    if(v < 0)  BigInt(v.toLong & 0xFFFFFFFFl) else  BigInt(v)
  })

  val io = new Bundle{
    val apb = slave(Apb3(log2Up(wordCount*4),32))
  }

  val rom = Mem(Bits(32 bits), wordCount) initBigInt(wordArray)
//  io.apb.PRDATA := rom.readSync(io.apb.PADDR >> 2)
  io.apb.PRDATA := rom.readSync(io.apb.PADDR >> 2, io.apb.PSEL(0) && !io.apb.PENABLE)
  io.apb.PREADY := True
  io.apb.PSLVERROR := False
}
