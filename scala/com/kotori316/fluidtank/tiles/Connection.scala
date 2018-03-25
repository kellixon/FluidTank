package com.kotori316.fluidtank.tiles

import com.kotori316.fluidtank.Utils
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.{Capability, ICapabilityProvider}
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler
import net.minecraftforge.fluids.capability.{CapabilityFluidHandler, FluidTankProperties, IFluidHandler, IFluidTankProperties}
import net.minecraftforge.fluids.{Fluid, FluidStack, FluidUtil}

class Connection(fisrt: TileTank, seq: Seq[TileTank]) extends ICapabilityProvider {

    val handler: IFluidHandler = new IFluidHandler {
        override def fill(kind: FluidStack, doFill: Boolean): Int = {
            if (kind == null || kind.amount <= 0) {
                return 0
            }
            val resource = kind.copy()
            var total = 0
            for (tileTank <- seq) {
                if (resource.amount > 0) {
                    val filled = tileTank.tank.fill(resource, doFill)
                    total += filled
                    resource.amount -= filled
                }
            }
            total
        }

        override def drain(kind: FluidStack, doDrain: Boolean): FluidStack = {
            if (kind == null || kind.amount <= 0) {
                return null
            }
            val resource = kind.copy()
            var total: FluidStack = null
            for (tileTank <- seq.reverse) {
                if (resource.amount > 0) {
                    val drained = tileTank.tank.drain(resource, doDrain)
                    if (drained != null) {
                        if (total == null) {
                            total = drained
                        } else {
                            total.amount += drained.amount
                        }
                    }
                }
            }
            total
        }

        override def drain(maxDrain: Int, doDrain: Boolean): FluidStack = {
            if (maxDrain <= 0) {
                return null
            }
            var toDrain = maxDrain
            var total: FluidStack = null
            for (tileTank <- seq.reverse) {
                if (toDrain > 0) {
                    if (total == null) {
                        total = tileTank.tank.drain(toDrain, doDrain)
                        if (total != null) {
                            toDrain -= total.amount
                        }
                    } else {
                        val copy = total.copy
                        copy.amount = toDrain
                        val drain = tileTank.tank.drain(copy, doDrain)
                        if (drain != null) {
                            total.amount += drain.amount
                            toDrain -= drain.amount
                        }
                    }
                }
            }
            total
        }

        override def getTankProperties: Array[IFluidTankProperties] = {
            val fluid = fluidType
            if (fluid != null) {
                Array(new FluidTankProperties(new FluidStack(fluidType, Utils.toInt(amount)), Utils.toInt(capacity)))
            } else {
                Array(new FluidTankProperties(null, Utils.toInt(capacity)))
            }
        }
    }

    def transfer() =
        seq.foreach(tileTank => FluidUtil.tryFluidTransfer(handler, tileTank.tank, tileTank.tank.getFluid, true))

    def fluidType = Option(fisrt.tank.getFluid).map(_.getFluid).orNull

    def capacity = seq.map(_.tier.amount.toLong).sum

    def amount = seq.map(_.tank.getFluidAmount.toLong).sum

    def hasChain = seq.size > 1

    override def getCapability[T](capability: Capability[T], facing: EnumFacing) = {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(handler)
        } else {
            null.asInstanceOf[T]
        }
    }

    override def hasCapability(capability: Capability[_], facing: EnumFacing) =
        capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY

    override def toString: String = {
        val fluid = fisrt.tank.getFluid
        if (fluid == null) {
            s"Connection of null : $amount / $capacity mB"
        } else {
            s"Connection of ${fluid.getLocalizedName} : $amount / $capacity mB"
        }
    }
}

object Connection {

    val invalid: Connection = new Connection(null, Nil) {
        override def fluidType: Fluid = null

        override def capacity: Long = 0

        override def amount: Long = 0

        override val handler: IFluidHandler = EmptyFluidHandler.INSTANCE

        override val toString: String = "Connection.Invalid"
    }

    def setConnection(tanks: Seq[TileTank], callback: Connection => TileTank => Unit): Unit = {
        val newConnection = Connection(tanks.headOption, tanks)
        tanks.foreach(callback(newConnection))
        newConnection.transfer()
    }

    def apply(fisrt: Option[TileTank], seq: Seq[TileTank]): Connection =
        fisrt match {
            case Some(tank) => new Connection(tank, seq)
            case None => invalid
        }
}