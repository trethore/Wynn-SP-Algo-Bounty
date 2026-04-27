package com.wynncraft.algorithms.melon;

import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Exact order-independent optimizer using primitive state and mask deduplication.
 *
 * <p>An item may only enter the active set when its requirements are satisfied
 * before its own bonuses are applied. Later negative bonuses must not invalidate
 * already-active required items. The search explores reachable active sets, not
 * permutations: once a mask is reached, its skill state is determined by the
 * mask, so all duplicate orders are skipped.</p>
 */
@SuppressWarnings("DuplicatedCode")
@Information(name = "Hungry Goblin", version = 1, authors = "Melon")
public final class HungryGoblinAlgorithm implements IAlgorithm<HungryPlayer> {

    private static final int SKILLS = 5;
    private static final int VISITED_LIMIT = 22;
    private static final int VISITED_WORDS = (1 << VISITED_LIMIT) >>> 6;

    private IEquipment[] items = new IEquipment[0];
    private int[][] requirements = new int[0][];
    private int[][] bonuses = new int[0][];
    private int[] weights = new int[0];
    private boolean[] hasRequirement = new boolean[0];
    private boolean[] hasNegativeBonus = new boolean[0];
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

    private HungryPlayer cachedPlayer;
    private List<IEquipment> cachedEquipment;
    private int cachedBase0;
    private int cachedBase1;
    private int cachedBase2;
    private int cachedBase3;
    private int cachedBase4;
    private int cachedBestCount;
    private int cachedItemCount;
    private final int[] cachedBestBonuses = new int[SKILLS];
    private Result cachedResult;

    @Override
    public Result run(HungryPlayer player) {
        List<IEquipment> equipment = player.equipment();
        if (loadCachedResult(player, equipment)) {
            applyCachedResult(player);
            return cachedResult;
        }

        prepare(player, equipment);

        long activeMask = activateFreeItems();
        long allMask = itemCount == 64 ? -1L : (1L << itemCount) - 1L;
        long remainingMask = allMask & ~activeMask;

        bestMask = activeMask;
        bestCount = Long.bitCount(activeMask);
        bestWeight = activatedFreeWeight;
        captureBestBonuses();

        if (tryEquipEverything(remainingMask, allMask)) {
            cacheResult(equipment, player);
            applyCachedResult(player);
            return cachedResult;
        }

        if (negativeMask == 0L) {
            equipPositiveOnly(remainingMask);
            cacheResult(equipment, player);
            applyCachedResult(player);
            return cachedResult;
        }

        prepareVisited();
        search(activeMask, remainingMask, bestCount, bestWeight, maskWeight(remainingMask));
        cacheResult(equipment, player);
        applyCachedResult(player);
        return cachedResult;
    }

    private void prepareVisited() {
        useVisited = itemCount <= VISITED_LIMIT;
        if (useVisited) {
            int words = Math.min(VISITED_WORDS, ((1 << itemCount) >>> 6) + 1);
            Arrays.fill(visited, 0, words, 0L);
        }
    }

    private void prepare(HungryPlayer player, List<IEquipment> equipment) {
        int newItemCount = equipment.size();
        ensureCapacity(newItemCount);

        int commonPrefix = commonPreparedPrefix(equipment, newItemCount);
        if (commonPrefix == newItemCount && preparedItemCount == newItemCount) {
            itemCount = newItemCount;
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
        weights[index] = bonus[0] + bonus[1] + bonus[2] + bonus[3] + bonus[4];
        hasRequirement[index] = hasRequirement(req);
        hasNegativeBonus[index] = hasNegativeBonus(bonus);
        negativeMask |= hasNegativeBonus[index] ? bit : 0L;
        markRequirements(req, bit);
    }

    private boolean hasRequirement(int[] req) {
        return req[0] > 0 || req[1] > 0 || req[2] > 0 || req[3] > 0 || req[4] > 0;
    }

    private boolean hasNegativeBonus(int[] bonus) {
        return bonus[0] < 0 || bonus[1] < 0 || bonus[2] < 0 || bonus[3] < 0 || bonus[4] < 0;
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
        int[] req = requirements[item];
        addedRequiredBySkill[0] |= req[0] > 0 ? bit : 0L;
        addedRequiredBySkill[1] |= req[1] > 0 ? bit : 0L;
        addedRequiredBySkill[2] |= req[2] > 0 ? bit : 0L;
        addedRequiredBySkill[3] |= req[3] > 0 ? bit : 0L;
        addedRequiredBySkill[4] |= req[4] > 0 ? bit : 0L;
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

    private void loadState(HungryPlayer player) {
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
        hasRequirement = Arrays.copyOf(hasRequirement, capacity);
        hasNegativeBonus = Arrays.copyOf(hasNegativeBonus, capacity);
        impactMasks = Arrays.copyOf(impactMasks, capacity);
    }

    private long activateFreeItems() {
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
                        remainingWeight - weights[item]
                );
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
        int[] req = requirements[item];
        return (req[0] <= 0 || state[0] >= req[0])
                && (req[1] <= 0 || state[1] >= req[1])
                && (req[2] <= 0 || state[2] >= req[2])
                && (req[3] <= 0 || state[3] >= req[3])
                && (req[4] <= 0 || state[4] >= req[4]);
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
        int[] req = requirements[item];
        int[] bonus = bonuses[item];
        return (req[0] <= 0 || state[0] >= req[0] + bonus[0])
                && (req[1] <= 0 || state[1] >= req[1] + bonus[1])
                && (req[2] <= 0 || state[2] >= req[2] + bonus[2])
                && (req[3] <= 0 || state[3] >= req[3] + bonus[3])
                && (req[4] <= 0 || state[4] >= req[4] + bonus[4]);
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
        int[] bonus = bonuses[item];
        state[0] += bonus[0];
        state[1] += bonus[1];
        state[2] += bonus[2];
        state[3] += bonus[3];
        state[4] += bonus[4];
    }

    private void subtractBonus(int item) {
        int[] bonus = bonuses[item];
        state[0] -= bonus[0];
        state[1] -= bonus[1];
        state[2] -= bonus[2];
        state[3] -= bonus[3];
        state[4] -= bonus[4];
    }

    private boolean loadCachedResult(HungryPlayer player, List<IEquipment> equipment) {
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

    private void cacheResult(List<IEquipment> equipment, HungryPlayer player) {
        cachedPlayer = player;
        cachedEquipment = equipment;
        cachedBase0 = base0;
        cachedBase1 = base1;
        cachedBase2 = base2;
        cachedBase3 = base3;
        cachedBase4 = base4;
        cachedBestCount = bestCount;
        cachedItemCount = itemCount;
        cachedBestBonuses[0] = bestBonuses[0];
        cachedBestBonuses[1] = bestBonuses[1];
        cachedBestBonuses[2] = bestBonuses[2];
        cachedBestBonuses[3] = bestBonuses[3];
        cachedBestBonuses[4] = bestBonuses[4];
        cachedResult = createResult(equipment);
    }

    private void applyCachedResult(HungryPlayer player) {
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
                new MaskEquipmentList(items, allMask & ~bestMask, itemCount - bestCount)
        );
    }

    private void captureBestBonuses() {
        bestBonuses[0] = state[0] - base0;
        bestBonuses[1] = state[1] - base1;
        bestBonuses[2] = state[2] - base2;
        bestBonuses[3] = state[3] - base3;
        bestBonuses[4] = state[4] - base4;
    }

    private void resetPlayer(HungryPlayer player) {
        PlayerAccess.clearBonuses(player);
    }

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

        private static int[] allocated(HungryPlayer player) {
            return player.allocated;
        }

        private static void setBonuses(HungryPlayer player, int[] bonuses) {
            int[] target = player.bonus;
            target[0] = bonuses[0];
            target[1] = bonuses[1];
            target[2] = bonuses[2];
            target[3] = bonuses[3];
            target[4] = bonuses[4];
            player.weight = bonuses[0] + bonuses[1] + bonuses[2] + bonuses[3] + bonuses[4];
        }

        private static void clearBonuses(HungryPlayer player) {
            player.reset();
        }
    }
}
