package mekanism.generators.common.content.turbine;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import mekanism.api.Action;
import mekanism.api.Coord4D;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.AutomationType;
import mekanism.api.math.FloatingLong;
import mekanism.api.math.MathUtils;
import mekanism.common.capabilities.chemical.MultiblockGasTank;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.energy.VariableCapacityEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.sync.dynamic.ContainerSync;
import mekanism.common.multiblock.MultiblockData;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import net.minecraft.fluid.Fluids;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

public class TurbineMultiblockData extends MultiblockData {

    public static final float ROTATION_THRESHOLD = 0.001F;
    public static Object2FloatMap<UUID> clientRotationMap = new Object2FloatOpenHashMap<>();

    @ContainerSync
    public MultiblockGasTank<TurbineMultiblockData> gasTank;
    @ContainerSync
    public IExtendedFluidTank ventTank;
    public List<IExtendedFluidTank> ventTanks;
    @ContainerSync
    public IEnergyContainer energyContainer;
    @ContainerSync
    public GasMode dumpMode = GasMode.IDLE;
    private FloatingLong energyCapacity = FloatingLong.ZERO;

    @ContainerSync
    public int blades, vents, coils, condensers;
    @ContainerSync
    public int lowerVolume;

    public Coord4D complex;

    @ContainerSync
    public long lastSteamInput;
    public long newSteamInput;

    @ContainerSync(getter = "getDispersers")
    public int clientDispersers;
    @ContainerSync
    public long clientFlow;

    public float clientRotation;
    public float prevSteamScale;

    public TurbineMultiblockData(TileEntityTurbineCasing tile) {
        gasTanks.add(gasTank = new TurbineGasTank(this, tile));
        ventTank = VariableCapacityFluidTank.create(() -> !isFormed() ? 1_000 : condensers * MekanismGeneratorsConfig.generators.condenserRate.get(),
              (stack, automationType) -> automationType != AutomationType.EXTERNAL || isFormed(), BasicFluidTank.internalOnly,
              fluid -> fluid.getFluid().isIn(FluidTags.WATER), null);
        ventTanks = Collections.singletonList(ventTank);
        energyContainer = VariableCapacityEnergyContainer.create(() -> getEnergyCapacity(),
              automationType -> automationType != AutomationType.EXTERNAL || isFormed(), BasicEnergyContainer.internalOnly, null);
        energyContainers.add(energyContainer);
    }

    @Override
    public boolean tick(World world) {
        boolean needsPacket = super.tick(world);

        lastSteamInput = newSteamInput;
        newSteamInput = 0;
        long stored = gasTank.getStored();
        double flowRate = 0;

        FloatingLong energyNeeded = energyContainer.getNeeded();
        if (stored > 0 && !energyNeeded.isZero()) {
            FloatingLong energyMultiplier = MekanismConfig.general.maxEnergyPerSteam.get().divide(TurbineUpdateProtocol.MAX_BLADES)
                                            .multiply(Math.min(blades, coils * MekanismGeneratorsConfig.generators.turbineBladesPerCoil.get()));
            if (energyMultiplier.isZero()) {
                clientFlow = 0;
            } else {
                double rate = lowerVolume * (getDispersers() * MekanismGeneratorsConfig.generators.turbineDisperserGasFlow.get());
                rate = Math.min(rate, vents * MekanismGeneratorsConfig.generators.turbineVentGasFlow.get());
                double proportion = stored / (double) getSteamCapacity();
                double origRate = rate;
                rate = Math.min(Math.min(stored, rate), energyNeeded.divide(energyMultiplier).doubleValue()) * proportion;

                flowRate = rate / origRate;
                energyContainer.insert(energyMultiplier.multiply(rate), Action.EXECUTE, AutomationType.INTERNAL);

                if (!gasTank.isEmpty()) {
                    gasTank.shrinkStack((long) rate, Action.EXECUTE);
                }
                clientFlow = (long) rate;
                ventTank.setStack(new FluidStack(Fluids.WATER, Math.min(MathUtils.clampToInt(rate), condensers * MekanismGeneratorsConfig.generators.condenserRate.get())));
            }
        } else {
            clientFlow = 0;
        }

        if (dumpMode == GasMode.DUMPING && !gasTank.isEmpty()) {
            long amount = gasTank.getStored();
            gasTank.shrinkStack(Math.min(amount, Math.max(amount / 50, lastSteamInput * 2)), Action.EXECUTE);
        }

        float newRotation = (float) flowRate;

        if (Math.abs(newRotation - clientRotation) > TurbineMultiblockData.ROTATION_THRESHOLD) {
            clientRotation = newRotation;
            needsPacket = true;
        }
        float scale = MekanismUtils.getScale(prevSteamScale, gasTank);
        if (scale != prevSteamScale) {
            needsPacket = true;
            prevSteamScale = scale;
        }
        return needsPacket;
    }

    public int getDispersers() {
        return (volLength - 2) * (volWidth - 2) - 1;
    }

    public long getSteamCapacity() {
        return lowerVolume * TurbineUpdateProtocol.GAS_PER_TANK;
    }

    public FloatingLong getEnergyCapacity() {
        return energyCapacity;
    }

    @Override
    public void setVolume(int volume) {
        super.setVolume(volume);
        energyCapacity = FloatingLong.createConst(getVolume() * 16_000_000L); //16 MJ energy capacity per volume
    }

    @Override
    protected int getMultiblockRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(gasTank.getStored(), gasTank.getCapacity());
    }
}