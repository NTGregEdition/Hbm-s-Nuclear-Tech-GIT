package api.hbm.energymk2;


import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import com.hbm.handler.threading.PacketThreading;
import com.hbm.interfaces.NotableComments;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;
import api.hbm.energymk2.Nodespace.PowerNode;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
/** If it receives energy, use this */
public interface IEnergyReceiverMK2 extends IEnergyHandlerMK2 {

	public default long getReceiverPower() { return this.getPower(); }
	public default void setReceiverPower(long power) { this.setPower(power); }
	public default long getReceiverMaxPower() { return this.getMaxPower(); }
	public default long getReceiverVoltage() { return this.getVoltage(); }

	Set<IEnergyReceiverMK2> BURNED_OUT = Collections.newSetFromMap(new WeakHashMap<IEnergyReceiverMK2, Boolean>());

	public default long transferPower(long power) {
		if(power + this.getReceiverPower() <= this.getReceiverMaxPower()) {
			this.setReceiverPower(power + this.getReceiverPower());
			return 0;
		}
		long capacity = this.getReceiverMaxPower() - this.getReceiverPower();
		long overshoot = power - capacity;
		this.setReceiverPower(this.getReceiverMaxPower());
		return overshoot;
	}


	public default long transferPowerAtVoltage(long power, long voltage) {
		if(power <= 0) return power;
		if(BURNED_OUT.contains(this)) return power;
		if(!VoltageTier.isConfigured(this.getReceiverVoltage())) return this.transferPower(power);
		if(voltage != this.getReceiverVoltage()) {
			this.onOvervoltage(voltage);
			return power;
		}
		return this.transferPower(power);
	}

	public default BlockPos[] getMultiblockPositions() {
		if(this instanceof TileEntity) {
			TileEntity te = (TileEntity) this;
			return new BlockPos[] { new BlockPos(te.xCoord, te.yCoord, te.zCoord) };
		}
		return new BlockPos[0];
	}

	public default void onOvervoltage(long voltage) {
		if(!BURNED_OUT.add(this)) return;
		if(this instanceof TileEntity) {
			TileEntity te = (TileEntity) this;
			World world = te.getWorldObj();
			if(world != null && !world.isRemote) {
				float strength = MachineVoltageRegistry.getExplosionStrength(this);
				for(BlockPos pos : this.getMultiblockPositions()) {
					world.setBlockToAir(pos.getX(), pos.getY(), pos.getZ());
				}
				world.createExplosion(null, te.xCoord + 0.5D, te.yCoord + 0.5D, te.zCoord + 0.5D, strength, true);
			}
		}
	}

	public default long getReceiverSpeed() {
		return this.getReceiverMaxPower();
	}

	public enum ConnectionPriority {
		LOWEST,
		LOW,
		NORMAL,
		HIGH,
		HIGHEST
	}

	public default ConnectionPriority getPriority() {
		return ConnectionPriority.NORMAL;
	}
}
