package com.enderdevelopment.forgottenfrontier;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForgottenFrontierMod implements ModInitializer {
    private static final SavedDataType<GeneratedData> DATA_TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("forgottenfrontier", "generated_sites"),
            GeneratedData::new, Codec.STRING.listOf().xmap(GeneratedData::new, d -> List.copyOf(d.keys)), DataFixTypes.LEVEL);

    @Override
    public void onInitialize() {
        ServerTickEvents.END_LEVEL_TICK.register(ForgottenFrontierMod::generateChunkSite);
        ServerPlayerEvents.JOIN.register(player -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!level.dimension().equals(ServerLevel.OVERWORLD)) return;
            GeneratedData data = level.getDataStorage().computeIfAbsent(DATA_TYPE);
            if (data.mapIssued) return;
            BlockPos spawn = level.getLevelData().getRespawnData().pos();
            player.getInventory().add(MapItem.create(level, spawn.getX(), spawn.getZ(), (byte) 1, true, true));
            data.mapIssued = true;
            data.setDirty();
        });
    }

    private static void generateChunkSite(ServerLevel level) {
        if (!level.dimension().equals(ServerLevel.OVERWORLD) || (level.getGameTime() & 31L) != 0L) return;
        BlockPos spawn = level.getLevelData().getRespawnData().pos();
        GeneratedData data = level.getDataStorage().computeIfAbsent(DATA_TYPE);
        for (ServerPlayer player : level.players()) {
            int cx = player.blockPosition().getX() >> 4;
            int cz = player.blockPosition().getZ() >> 4;
            long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
            if (Math.abs(cx * 16L - spawn.getX()) < 96 && Math.abs(cz * 16L - spawn.getZ()) < 96) continue;
            String text = Long.toString(key);
            if (data.keys.contains(text) || level.getChunkSource().getChunkNow(cx, cz) == null) continue;
            data.keys.add(text);
            data.setDirty();
            RandomSource random = RandomSource.create(level.getSeed() ^ key);
            if (random.nextInt(12) != 0) continue;
            int x = cx * 16 + 3 + random.nextInt(10);
            int z = cz * 16 + 3 + random.nextInt(10);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            if (y < level.getMinY() + 2 || y > level.getMaxY() - 12) continue;
            buildSite(level, new BlockPos(x, y, z), random, random.nextInt(3));
        }
    }

    private static void buildSite(ServerLevel level, BlockPos origin, RandomSource random, int type) {
        var wall = type == 2 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        var floor = type == 1 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        int size = type == 1 ? 9 : 7;
        for (int dx = -size / 2; dx <= size / 2; dx++) for (int dz = -size / 2; dz <= size / 2; dz++) {
            boolean edge = Math.abs(dx) == size / 2 || Math.abs(dz) == size / 2;
            level.setBlock(origin.offset(dx, 0, dz), floor, 3);
            for (int dy = 1; dy <= 4; dy++) {
                if (!edge || random.nextInt(5) == 0 || (type != 2 && dy == 3 && random.nextBoolean())) continue;
                level.setBlock(origin.offset(dx, dy, dz), wall, 3);
            }
        }
        for (int i = 0; i < (type == 1 ? 5 : 3); i++) {
            BlockPos pos = origin.offset(random.nextInt(size) - size / 2, 1, random.nextInt(size) - size / 2);
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                fillChest(chest, random, type == 1);
                chest.setChanged();
            }
        }
        if (type == 1) {
            level.setBlock(origin.offset(0, 1, -size / 2), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            level.setBlock(origin.offset(1, 1, -size / 2), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        }
    }

    private static void fillChest(ChestBlockEntity chest, RandomSource random, boolean bank) {
        ItemStack[] loot = bank
                ? new ItemStack[]{new ItemStack(Items.EMERALD, 4 + random.nextInt(13)), new ItemStack(Items.GOLD_INGOT, 3 + random.nextInt(9)), new ItemStack(Items.DIAMOND, 1 + random.nextInt(4)), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)}
                : new ItemStack[]{new ItemStack(Items.EMERALD, 1 + random.nextInt(5)), new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(4)), new ItemStack(Items.DIAMOND), new ItemStack(Items.EXPERIENCE_BOTTLE, 2 + random.nextInt(7)), new ItemStack(Items.GOLDEN_CARROT, 2 + random.nextInt(5))};
        for (ItemStack stack : loot) if (random.nextBoolean()) chest.setItem(random.nextInt(chest.getContainerSize()), stack);
    }

    private static final class GeneratedData extends SavedData {
        private final Set<String> keys;
        private boolean mapIssued;
        private GeneratedData() { keys = new HashSet<>(); }
        private GeneratedData(List<String> saved) { keys = new HashSet<>(saved); }
    }
}
