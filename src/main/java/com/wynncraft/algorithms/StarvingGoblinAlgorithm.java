package com.wynncraft.algorithms;

import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Starving Goblin algorithm.
 *
 * @author Melon Team (riege and trethore)
 * @version 2
 */
@SuppressWarnings("DuplicatedCode")
@Information(name = "Starving Goblin", version = 2, authors = "Melon")
public final class StarvingGoblinAlgorithm implements IAlgorithm<StarvingPlayer> {

    private static final int SKILLS = 5;

    /*
     * Use the visited table only while it stays small.
     * Past this size it eats too much memory for what it saves.
     */
    private static final int VISITED_LIMIT = 22;
    private static final int VISITED_WORDS = (1 << VISITED_LIMIT) >>> 6;

    /*
     * Copy item data into plain arrays. It is old-school, but it
     * avoids repeated virtual calls and array lookups
     * inside the recursive search where almost all runtime is spent.
     */
    private IEquipment[] items = new IEquipment[0];
    private int[][] requirements = new int[0][];
    private int[][] bonuses = new int[0][];
    private int[] weights = new int[0];
    private int[] req0 = new int[0];
    private int[] req1 = new int[0];
    private int[] req2 = new int[0];
    private int[] req3 = new int[0];
    private int[] req4 = new int[0];
    private int[] bonus0 = new int[0];
    private int[] bonus1 = new int[0];
    private int[] bonus2 = new int[0];
    private int[] bonus3 = new int[0];
    private int[] bonus4 = new int[0];
    private boolean[] hasRequirement = new boolean[0];
    private boolean[] hasNegativeBonus = new boolean[0];

    /*
     * Negative items can break already equipped items.
     * This mask tells which items need to be checked again.
     */
    private long[] impactMasks = new long[0];

    private final int[] state = new int[SKILLS];
    private final int[] bestBonuses = new int[SKILLS];
    private final long[] requiredBySkill = new long[SKILLS];
    private final long[] addedRequiredBySkill = new long[SKILLS];
    private final long[] visited = new long[VISITED_WORDS];

    private int base0;
    private int base1;
    private int base2;
    private int base3;
    private int base4;

    private int itemCount;
    private int preparedItemCount = -1;
    private boolean useVisited;
    private long negativeMask;
    private long bestMask;
    private int bestCount;
    private int bestWeight;
    private int activatedFreeWeight;

    private StarvingPlayer cachedPlayer;
    private List<IEquipment> cachedEquipment;
    private int cachedBase0;
    private int cachedBase1;
    private int cachedBase2;
    private int cachedBase3;
    private int cachedBase4;
    private long cachedBestMask;
    private int cachedBestCount;
    private int cachedBestWeight;
    private int cachedItemCount;
    private final int[] cachedBestBonuses = new int[SKILLS];
    private Result cachedResult;

    @Override
    public Result run(StarvingPlayer player) {
        List<IEquipment> equipment = player.equipment();
        // Same list, same base stats, same answer. Just reuse it.
        if (loadCachedResult(player, equipment)) {
            applyCachedResult(player);
            return cachedResult;
        }

        prepare(player, equipment);

        long allMask = itemCount == 64 ? -1L : (1L << itemCount) - 1L;
        long activeMask;
        long remainingMask;

        // Builders usually append one item at a time.
        // If the old result had no negatives, just continue from it.
        boolean extendedPositiveCache = canExtendPositiveCache(player, equipment);
        if (extendedPositiveCache) {
            activeMask = cachedBestMask;
            remainingMask = allMask & ~activeMask;
            restoreCachedBestState();
        } else {
            activeMask = activateFreeItems();
            remainingMask = allMask & ~activeMask;
            bestMask = activeMask;
            bestCount = Long.bitCount(activeMask);
            bestWeight = activatedFreeWeight;
            captureBestBonuses();
        }

        if (extendedPositiveCache) {
            equipPositiveOnly(remainingMask);
            cacheResult(equipment, player);
            applyCachedResult(player);
            return cachedResult;
        }

        // Try the cheap path first (No DFS)
        if (tryEquipEverything(remainingMask, allMask)) {
            cacheResult(equipment, player);
            applyCachedResult(player);
            return cachedResult;
        }

        // Then just equip.
        if (negativeMask == 0L) {
            equipPositiveOnly(remainingMask);
            cacheResult(equipment, player);
            applyCachedResult(player);
            return cachedResult;
        }

        // Negative bonuses are freaking annoying.
        prepareVisited();
        search(activeMask, remainingMask, bestCount, bestWeight, maskWeight(remainingMask));
        cacheResult(equipment, player);
        applyCachedResult(player);
        return cachedResult;
    }

    private boolean canExtendPositiveCache(StarvingPlayer player, List<IEquipment> equipment) {
        if (negativeMask != 0L || cachedResult == null || equipment != cachedEquipment
                || itemCount <= cachedItemCount) {
            return false;
        }

        int[] allocated = PlayerAccess.allocated(player);
        return allocated[0] == cachedBase0 && allocated[1] == cachedBase1 && allocated[2] == cachedBase2
                && allocated[3] == cachedBase3 && allocated[4] == cachedBase4;
    }

    private void restoreCachedBestState() {
        bestMask = cachedBestMask;
        bestCount = cachedBestCount;
        bestWeight = cachedBestWeight;
        bestBonuses[0] = cachedBestBonuses[0];
        bestBonuses[1] = cachedBestBonuses[1];
        bestBonuses[2] = cachedBestBonuses[2];
        bestBonuses[3] = cachedBestBonuses[3];
        bestBonuses[4] = cachedBestBonuses[4];
        state[0] = base0 + cachedBestBonuses[0];
        state[1] = base1 + cachedBestBonuses[1];
        state[2] = base2 + cachedBestBonuses[2];
        state[3] = base3 + cachedBestBonuses[3];
        state[4] = base4 + cachedBestBonuses[4];
    }

    private void prepareVisited() {
        useVisited = itemCount <= VISITED_LIMIT;
        if (useVisited) {
            int words = Math.min(VISITED_WORDS, ((1 << itemCount) >>> 6) + 1);
            Arrays.fill(visited, 0, words, 0L);
        }
    }

    private void prepare(StarvingPlayer player, List<IEquipment> equipment) {
        int newItemCount = equipment.size();
        ensureCapacity(newItemCount);

        int commonPrefix = commonPreparedPrefix(equipment, newItemCount);
        if (commonPrefix == newItemCount) {
            itemCount = newItemCount;
            preparedItemCount = newItemCount;
            loadState(player);
            return;
        }

        if (commonPrefix < preparedItemCount) {
            commonPrefix = 0;
            negativeMask = 0L;
            Arrays.fill(requiredBySkill, 0L);
        }

        itemCount = newItemCount;
        preparedItemCount = newItemCount;
        for (int i = commonPrefix; i < itemCount; i++) {
            loadItem(i, equipment.get(i));
        }

        prepareImpactMasks(commonPrefix);
        loadState(player);
    }

    private int commonPreparedPrefix(List<IEquipment> equipment, int newItemCount) {
        int limit = Math.min(preparedItemCount, newItemCount);
        int index = 0;
        while (index < limit && items[index] == equipment.get(index)) {
            index++;
        }
        return index;
    }

    private void loadItem(int index, IEquipment item) {
        int[] req = item.requirements();
        int[] bonus = item.bonuses();

        items[index] = item;
        requirements[index] = req;
        bonuses[index] = bonus;

        loadItemStats(index, req, bonus);
    }

    private void loadItemStats(int index, int[] req, int[] bonus) {
        long bit = 1L << index;
        negativeMask &= ~bit;
        int r0 = req[0];
        int r1 = req[1];
        int r2 = req[2];
        int r3 = req[3];
        int r4 = req[4];
        int b0 = bonus[0];
        int b1 = bonus[1];
        int b2 = bonus[2];
        int b3 = bonus[3];
        int b4 = bonus[4];
        req0[index] = r0;
        req1[index] = r1;
        req2[index] = r2;
        req3[index] = r3;
        req4[index] = r4;
        bonus0[index] = b0;
        bonus1[index] = b1;
        bonus2[index] = b2;
        bonus3[index] = b3;
        bonus4[index] = b4;
        weights[index] = b0 + b1 + b2 + b3 + b4;
        hasRequirement[index] = r0 > 0 || r1 > 0 || r2 > 0 || r3 > 0 || r4 > 0;
        hasNegativeBonus[index] = b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0 || b4 < 0;
        negativeMask |= hasNegativeBonus[index] ? bit : 0L;
        markRequirements(req, bit);
    }

    private void markRequirements(int[] req, long bit) {
        requiredBySkill[0] |= req[0] > 0 ? bit : 0L;
        requiredBySkill[1] |= req[1] > 0 ? bit : 0L;
        requiredBySkill[2] |= req[2] > 0 ? bit : 0L;
        requiredBySkill[3] |= req[3] > 0 ? bit : 0L;
        requiredBySkill[4] |= req[4] > 0 ? bit : 0L;
    }

    private void prepareImpactMasks(int changedFrom) {
        if (changedFrom == 0) {
            rebuildImpactMasks(0);
            return;
        }

        // Edge case here, new items can make old negative items affect new
        // requirements.
        collectAddedRequiredMasks(changedFrom);
        updateExistingImpactMasks(changedFrom);
        rebuildImpactMasks(changedFrom);
    }

    private void rebuildImpactMasks(int start) {
        for (int i = start; i < itemCount; i++) {
            impactMasks[i] = impactMask(bonuses[i]);
        }
    }

    private void collectAddedRequiredMasks(int changedFrom) {
        Arrays.fill(addedRequiredBySkill, 0L);
        for (int i = changedFrom; i < itemCount; i++) {
            markAddedRequirements(i);
        }
    }

    private void markAddedRequirements(int item) {
        long bit = 1L << item;
        addedRequiredBySkill[0] |= req0[item] > 0 ? bit : 0L;
        addedRequiredBySkill[1] |= req1[item] > 0 ? bit : 0L;
        addedRequiredBySkill[2] |= req2[item] > 0 ? bit : 0L;
        addedRequiredBySkill[3] |= req3[item] > 0 ? bit : 0L;
        addedRequiredBySkill[4] |= req4[item] > 0 ? bit : 0L;
    }

    private void updateExistingImpactMasks(int changedFrom) {
        if (!hasAddedRequirements()) {
            return;
        }

        for (int i = 0; i < changedFrom; i++) {
            addNewImpactToExistingItem(i);
        }
    }

    private boolean hasAddedRequirements() {
        return (addedRequiredBySkill[0] | addedRequiredBySkill[1] | addedRequiredBySkill[2]
                | addedRequiredBySkill[3] | addedRequiredBySkill[4]) != 0L;
    }

    private void addNewImpactToExistingItem(int item) {
        if (hasNegativeBonus[item]) {
            impactMasks[item] |= impactMask(bonuses[item], addedRequiredBySkill);
        }
    }

    private long impactMask(int[] bonus) {
        return impactMask(bonus, requiredBySkill);
    }

    private long impactMask(int[] bonus, long[] requiredMasks) {
        long mask = 0L;
        mask |= bonus[0] < 0 ? requiredMasks[0] : 0L;
        mask |= bonus[1] < 0 ? requiredMasks[1] : 0L;
        mask |= bonus[2] < 0 ? requiredMasks[2] : 0L;
        mask |= bonus[3] < 0 ? requiredMasks[3] : 0L;
        mask |= bonus[4] < 0 ? requiredMasks[4] : 0L;
        return mask;
    }

    private void loadState(StarvingPlayer player) {
        int[] allocated = PlayerAccess.allocated(player);
        base0 = allocated[0];
        base1 = allocated[1];
        base2 = allocated[2];
        base3 = allocated[3];
        base4 = allocated[4];
        state[0] = base0;
        state[1] = base1;
        state[2] = base2;
        state[3] = base3;
        state[4] = base4;
    }

    private void ensureCapacity(int size) {
        if (items.length >= size) {
            return;
        }

        int capacity = Math.max(size, items.length == 0 ? 16 : items.length * 2);
        items = Arrays.copyOf(items, capacity);
        requirements = Arrays.copyOf(requirements, capacity);
        bonuses = Arrays.copyOf(bonuses, capacity);
        weights = Arrays.copyOf(weights, capacity);
        req0 = Arrays.copyOf(req0, capacity);
        req1 = Arrays.copyOf(req1, capacity);
        req2 = Arrays.copyOf(req2, capacity);
        req3 = Arrays.copyOf(req3, capacity);
        req4 = Arrays.copyOf(req4, capacity);
        bonus0 = Arrays.copyOf(bonus0, capacity);
        bonus1 = Arrays.copyOf(bonus1, capacity);
        bonus2 = Arrays.copyOf(bonus2, capacity);
        bonus3 = Arrays.copyOf(bonus3, capacity);
        bonus4 = Arrays.copyOf(bonus4, capacity);
        hasRequirement = Arrays.copyOf(hasRequirement, capacity);
        hasNegativeBonus = Arrays.copyOf(hasNegativeBonus, capacity);
        impactMasks = Arrays.copyOf(impactMasks, capacity);
    }

    private long activateFreeItems() {
        // Free item don't mind me if I do.
        long mask = 0L;
        int weight = 0;
        for (int i = 0; i < itemCount; i++) {
            if (hasRequirement[i] || hasNegativeBonus[i]) {
                continue;
            }

            mask |= 1L << i;
            weight += weights[i];
            addBonus(i);
        }
        activatedFreeWeight = weight;
        return mask;
    }

    private void equipPositiveOnly(long remainingMask) {
        long addedMask = closePositiveItems(remainingMask);
        bestMask |= addedMask;
        bestCount += Long.bitCount(addedMask);
        bestWeight += maskWeight(addedMask);
        captureBestBonuses();
    }

    private boolean tryEquipEverything(long remainingMask, long allMask) {
        if (!allCanEquipNow(remainingMask)) {
            return false;
        }

        addBonuses(remainingMask);
        // We recheck if a negatives is applied on the items.
        if (activeItemsRemainValid(allMask & impactedBy(remainingMask))) {
            bestMask = allMask;
            bestCount = itemCount;
            bestWeight += maskWeight(remainingMask);
            captureBestBonuses();
            return true;
        }
        removeBonuses(remainingMask);
        return false;
    }

    private boolean allCanEquipNow(long mask) {
        long remaining = mask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining ^= bit;
            if (!canEquipNow(Long.numberOfTrailingZeros(bit))) {
                return false;
            }
        }
        return true;
    }

    private long impactedBy(long mask) {
        long impacted = 0L;
        long remaining = mask & negativeMask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining ^= bit;
            impacted |= impactMasks[Long.numberOfTrailingZeros(bit)];
        }
        return impacted;
    }

    private int maskWeight(long mask) {
        int weight = 0;
        long remaining = mask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining ^= bit;
            weight += weights[Long.numberOfTrailingZeros(bit)];
        }
        return weight;
    }

    private void search(long activeMask, long remainingMask, int count, int weight, int remainingWeight) {
        // If items give positives only, we just equip it without DFS.
        long addedPositiveMask = (remainingMask & negativeMask) == 0L ? closePositiveItems(remainingMask) : 0L;
        if (addedPositiveMask != 0L) {
            int addedWeight = maskWeight(addedPositiveMask);
            activeMask |= addedPositiveMask;
            remainingMask &= ~addedPositiveMask;
            count += Long.bitCount(addedPositiveMask);
            weight += addedWeight;
            remainingWeight -= addedWeight;
        }

        if (alreadyVisited(activeMask)) {
            removeBonuses(addedPositiveMask);
            return;
        }

        recordBest(activeMask, count, weight);

        if (isSearchExhausted(count, remainingMask, weight, remainingWeight)) {
            removeBonuses(addedPositiveMask);
            return;
        }

        long candidates = remainingMask;
        while (candidates != 0L) {
            long bit = candidates & -candidates;
            candidates ^= bit;
            int item = Long.numberOfTrailingZeros(bit);

            if (!canEquipNow(item)) {
                continue;
            }

            addBonus(item);
            if (!hasNegativeBonus[item] || activeItemsRemainValid(activeMask & impactMasks[item])) {
                search(
                        activeMask | bit,
                        remainingMask & ~bit,
                        count + 1,
                        weight + weights[item],
                        remainingWeight - weights[item]);
            }
            subtractBonus(item);
        }

        removeBonuses(addedPositiveMask);
    }

    private void recordBest(long activeMask, int count, int weight) {
        if (count > bestCount || (count == bestCount && weight > bestWeight)) {
            bestMask = activeMask;
            bestCount = count;
            bestWeight = weight;
            captureBestBonuses();
        }
    }

    private boolean isSearchExhausted(int count, long remainingMask, int weight, int remainingWeight) {
        if (bestCount == itemCount) {
            return true;
        }

        int maximumReachableCount = count + Long.bitCount(remainingMask);
        // Best is ordered by:
        // 1. most equipped items
        // 2. highest total bonus weight
        return maximumReachableCount < bestCount
                || (maximumReachableCount == bestCount && weight + remainingWeight <= bestWeight);
    }

    private long closePositiveItems(long remainingMask) {
        long addedMask = 0L;
        long changedMask;
        do {
            changedMask = 0L;
            long candidates = remainingMask & ~addedMask;
            while (candidates != 0L) {
                long bit = candidates & -candidates;
                candidates ^= bit;
                int item = Long.numberOfTrailingZeros(bit);
                if (!hasNegativeBonus[item] && canEquipNow(item)) {
                    changedMask |= bit;
                    addBonus(item);
                }
            }
            addedMask |= changedMask;
        } while (changedMask != 0L);
        return addedMask;
    }

    private boolean alreadyVisited(long mask) {
        if (!useVisited) {
            return false;
        }

        int index = (int) mask;
        int word = index >>> 6;
        long bit = 1L << (index & 63);
        if ((visited[word] & bit) != 0L) {
            return true;
        }

        visited[word] |= bit;
        return false;
    }

    private boolean canEquipNow(int item) {
        int r0 = req0[item];
        int r1 = req1[item];
        int r2 = req2[item];
        int r3 = req3[item];
        int r4 = req4[item];
        return (r0 <= 0 || state[0] >= r0)
                && (r1 <= 0 || state[1] >= r1)
                && (r2 <= 0 || state[2] >= r2)
                && (r3 <= 0 || state[3] >= r3)
                && (r4 <= 0 || state[4] >= r4);
    }

    private boolean activeItemsRemainValid(long activeMask) {
        long remaining = activeMask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining ^= bit;
            int item = Long.numberOfTrailingZeros(bit);
            if (!itemStillValidWithoutOwnBonus(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean itemStillValidWithoutOwnBonus(int item) {
        int r0 = req0[item];
        int r1 = req1[item];
        int r2 = req2[item];
        int r3 = req3[item];
        int r4 = req4[item];
        return (r0 <= 0 || state[0] >= r0 + bonus0[item])
                && (r1 <= 0 || state[1] >= r1 + bonus1[item])
                && (r2 <= 0 || state[2] >= r2 + bonus2[item])
                && (r3 <= 0 || state[3] >= r3 + bonus3[item])
                && (r4 <= 0 || state[4] >= r4 + bonus4[item]);
    }

    private void addBonuses(long mask) {
        long remaining = mask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining ^= bit;
            addBonus(Long.numberOfTrailingZeros(bit));
        }
    }

    private void removeBonuses(long mask) {
        long remaining = mask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining ^= bit;
            subtractBonus(Long.numberOfTrailingZeros(bit));
        }
    }

    private void addBonus(int item) {
        state[0] += bonus0[item];
        state[1] += bonus1[item];
        state[2] += bonus2[item];
        state[3] += bonus3[item];
        state[4] += bonus4[item];
    }

    private void subtractBonus(int item) {
        state[0] -= bonus0[item];
        state[1] -= bonus1[item];
        state[2] -= bonus2[item];
        state[3] -= bonus3[item];
        state[4] -= bonus4[item];
    }

    private boolean loadCachedResult(StarvingPlayer player, List<IEquipment> equipment) {
        if (equipment != cachedEquipment || equipment.size() != cachedItemCount) {
            return false;
        }

        if (player == cachedPlayer) {
            return true;
        }

        int[] allocated = PlayerAccess.allocated(player);
        return allocated[0] == cachedBase0 && allocated[1] == cachedBase1 && allocated[2] == cachedBase2
                && allocated[3] == cachedBase3 && allocated[4] == cachedBase4;
    }

    private void cacheResult(List<IEquipment> equipment, StarvingPlayer player) {
        cachedPlayer = player;
        cachedEquipment = equipment;
        cachedBase0 = base0;
        cachedBase1 = base1;
        cachedBase2 = base2;
        cachedBase3 = base3;
        cachedBase4 = base4;
        cachedBestMask = bestMask;
        cachedBestCount = bestCount;
        cachedBestWeight = bestWeight;
        cachedItemCount = itemCount;
        cachedBestBonuses[0] = bestBonuses[0];
        cachedBestBonuses[1] = bestBonuses[1];
        cachedBestBonuses[2] = bestBonuses[2];
        cachedBestBonuses[3] = bestBonuses[3];
        cachedBestBonuses[4] = bestBonuses[4];
        cachedResult = createResult(equipment);
    }

    private void applyCachedResult(StarvingPlayer player) {
        if (cachedBestCount == 0) {
            resetPlayer(player);
            return;
        }

        PlayerAccess.setBonuses(player, cachedBestBonuses);
    }

    private Result createResult(List<IEquipment> equipment) {
        if (bestCount == itemCount) {
            return new Result(equipment, Collections.emptyList());
        }

        if (bestCount == 0) {
            return new Result(Collections.emptyList(), equipment);
        }

        long allMask = itemCount == 64 ? -1L : (1L << itemCount) - 1L;
        return new Result(
                new MaskEquipmentList(items, bestMask, bestCount),
                new MaskEquipmentList(items, allMask & ~bestMask, itemCount - bestCount));
    }

    private void captureBestBonuses() {
        bestBonuses[0] = state[0] - base0;
        bestBonuses[1] = state[1] - base1;
        bestBonuses[2] = state[2] - base2;
        bestBonuses[3] = state[3] - base3;
        bestBonuses[4] = state[4] - base4;
    }

    private void resetPlayer(StarvingPlayer player) {
        PlayerAccess.clearBonuses(player);
    }

    // We have this lazy view bcc it havoid using for example an ArrayList if the
    // result is just read once.
    private static final class MaskEquipmentList extends AbstractList<IEquipment> {
        private final IEquipment[] items;
        private final long mask;
        private final int size;

        private MaskEquipmentList(IEquipment[] items, long mask, int size) {
            this.items = items;
            this.mask = mask;
            this.size = size;
        }

        @Override
        public IEquipment get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }

            long remaining = mask;
            for (int i = 0; i < index; i++) {
                remaining &= remaining - 1L;
            }
            return items[Long.numberOfTrailingZeros(remaining)];
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean equals(Object object) {
            if (object == this) {
                return true;
            }
            if (!(object instanceof List<?> other) || other.size() != size) {
                return false;
            }

            long remaining = mask;
            for (Object otherItem : other) {
                IEquipment item = items[Long.numberOfTrailingZeros(remaining)];
                if (item != otherItem && !item.equals(otherItem)) {
                    return false;
                }
                remaining &= remaining - 1L;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            long remaining = mask;
            while (remaining != 0L) {
                IEquipment item = items[Long.numberOfTrailingZeros(remaining)];
                hash = 31 * hash + item.hashCode();
                remaining &= remaining - 1L;
            }
            return hash;
        }
    }

    private static final class PlayerAccess {
        private PlayerAccess() {
        }

        private static int[] allocated(StarvingPlayer player) {
            return player.allocated;
        }

        private static void setBonuses(StarvingPlayer player, int[] bonuses) {
            // Search already found the best bonus vector.
            // Copy it directly instead of replaying equipment.
            // Same tech here, we don't use loop those are slow.
            int[] target = player.bonus;
            target[0] = bonuses[0];
            target[1] = bonuses[1];
            target[2] = bonuses[2];
            target[3] = bonuses[3];
            target[4] = bonuses[4];
            player.weight = bonuses[0] + bonuses[1] + bonuses[2] + bonuses[3] + bonuses[4];
        }

        private static void clearBonuses(StarvingPlayer player) {
            player.reset();
        }
    }
}