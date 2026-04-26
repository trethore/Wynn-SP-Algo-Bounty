package com.wynncraft.algorithms.melon;

import com.wynncraft.core.WynnPlayer;
import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;
import com.wynncraft.enums.SkillPoint;

import java.util.ArrayList;
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
@Information(name = "Hungry Goblin", version = 1, authors = "Melon")
public final class HungryGoblinAlgorithm implements IAlgorithm<WynnPlayer> {

    private static final SkillPoint[] SKILL_POINTS = SkillPoint.values();
    private static final int SKILLS = SKILL_POINTS.length;
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
    private final int[] playerBonuses = new int[SKILLS];
    private final long[] requiredBySkill = new long[SKILLS];
    private final long[] visited = new long[VISITED_WORDS];

    private int itemCount;
    private boolean useVisited;
    private long negativeMask;
    private long bestMask;
    private int bestCount;
    private int bestWeight;

    @Override
    public Result run(WynnPlayer player) {
        prepare(player);

        long activeMask = activateFreeItems();
        long allMask = itemCount == 64 ? -1L : (1L << itemCount) - 1L;
        long remainingMask = allMask & ~activeMask;

        useVisited = itemCount <= VISITED_LIMIT;
        if (useVisited) {
            int words = Math.min(VISITED_WORDS, ((1 << itemCount) >>> 6) + 1);
            Arrays.fill(visited, 0, words, 0L);
        }

        bestMask = activeMask;
        bestCount = Long.bitCount(activeMask);
        bestWeight = maskWeight(activeMask);

        if (tryEquipEverything(remainingMask, allMask)) {
            return buildResult(player);
        }

        search(activeMask, remainingMask, bestCount, bestWeight, maskWeight(remainingMask));
        return buildResult(player);
    }

    private void prepare(WynnPlayer player) {
        List<IEquipment> equipment = player.equipment();
        itemCount = equipment.size();
        ensureCapacity(itemCount);

        negativeMask = 0L;
        Arrays.fill(requiredBySkill, 0L);
        for (int i = 0; i < itemCount; i++) {
            loadItem(i, equipment.get(i));
        }

        prepareImpactMasks();
        loadState(player);
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
        int weight = 0;
        boolean reqPresent = false;
        boolean negativePresent = false;
        long bit = 1L << index;
        for (int skill = 0; skill < SKILLS; skill++) {
            weight += bonus[skill];
            reqPresent |= req[skill] > 0;
            negativePresent |= bonus[skill] < 0;
            markRequirement(skill, req[skill], bit);
        }
        weights[index] = weight;
        hasRequirement[index] = reqPresent;
        hasNegativeBonus[index] = negativePresent;
        if (negativePresent) {
            negativeMask |= bit;
        }
    }

    private void markRequirement(int skill, int requirement, long bit) {
        if (requirement > 0) {
            requiredBySkill[skill] |= bit;
        }
    }

    private void prepareImpactMasks() {
        for (int i = 0; i < itemCount; i++) {
            impactMasks[i] = impactMask(bonuses[i]);
        }
    }

    private long impactMask(int[] bonus) {
        long mask = 0L;
        mask |= bonus[0] < 0 ? requiredBySkill[0] : 0L;
        mask |= bonus[1] < 0 ? requiredBySkill[1] : 0L;
        mask |= bonus[2] < 0 ? requiredBySkill[2] : 0L;
        mask |= bonus[3] < 0 ? requiredBySkill[3] : 0L;
        mask |= bonus[4] < 0 ? requiredBySkill[4] : 0L;
        return mask;
    }

    private void loadState(WynnPlayer player) {
        for (int s = 0; s < SKILLS; s++) {
            state[s] = player.allocated(SKILL_POINTS[s]);
        }
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
        for (int i = 0; i < itemCount; i++) {
            if (hasRequirement[i] || hasNegativeBonus[i]) {
                continue;
            }

            mask |= 1L << i;
            addBonus(i);
        }
        return mask;
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

        if (count > bestCount || (count == bestCount && weight > bestWeight)) {
            bestMask = activeMask;
            bestCount = count;
            bestWeight = weight;
        }

        int maximumReachableCount = count + Long.bitCount(remainingMask);
        if (maximumReachableCount < bestCount) {
            removeBonuses(addedPositiveMask);
            return;
        }
        if (maximumReachableCount == bestCount && weight + remainingWeight <= bestWeight) {
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

    private Result buildResult(WynnPlayer player) {
        if (bestCount == itemCount) {
            List<IEquipment> valid = allItemsList();
            applyResult(player, valid);
            return new Result(valid, Collections.emptyList());
        }

        if (bestCount == 0) {
            resetPlayer(player);
            return new Result(Collections.emptyList(), allItemsList());
        }

        return buildPartialResult(player);
    }

    private Result buildPartialResult(WynnPlayer player) {
        List<IEquipment> valid = new ArrayList<>(bestCount);
        List<IEquipment> invalid = new ArrayList<>(itemCount - bestCount);
        for (int i = 0; i < itemCount; i++) {
            if ((bestMask & (1L << i)) != 0L) {
                valid.add(items[i]);
            } else {
                invalid.add(items[i]);
            }
        }
        applyResult(player, valid);
        return new Result(valid, invalid);
    }

    private List<IEquipment> allItemsList() {
        return new ArrayList<>(Arrays.asList(Arrays.copyOf(items, itemCount)));
    }

    private void applyResult(WynnPlayer player, List<IEquipment> valid) {
        resetPlayer(player);
        for (IEquipment item : valid) {
            player.modify(item.bonuses(), true);
        }
    }

    private void resetPlayer(WynnPlayer player) {
        for (int skill = 0; skill < SKILLS; skill++) {
            SkillPoint skillPoint = SKILL_POINTS[skill];
            playerBonuses[skill] = player.total(skillPoint) - player.allocated(skillPoint);
        }
        player.modify(playerBonuses, false);
    }
}
