package com.wynncraft.algorithms.melon;

import com.wynncraft.core.WynnPlayer;
import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;
import com.wynncraft.enums.SkillPoint;

import java.util.ArrayList;
import java.util.Arrays;
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
@Information(name = "Exact Skill Optimizer", version = 1, authors = "Melon")
public final class ExactSubsetOptimizerAlgorithm implements IAlgorithm<WynnPlayer> {

    private static final int SKILLS = SkillPoint.values().length;
    private static final int VISITED_LIMIT = 22;
    private static final int VISITED_WORDS = (1 << VISITED_LIMIT) >>> 6;

    private IEquipment[] items = new IEquipment[0];
    private int[][] requirements = new int[0][];
    private int[][] bonuses = new int[0][];
    private int[] weights = new int[0];
    private boolean[] hasRequirement = new boolean[0];
    private boolean[] hasNegativeBonus = new boolean[0];

    private final int[] state = new int[SKILLS];
    private final long[] visited = new long[VISITED_WORDS];

    private int itemCount;
    private boolean useVisited;
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

        search(activeMask, remainingMask, bestCount, bestWeight);
        return buildResult(player);
    }

    private void prepare(WynnPlayer player) {
        List<IEquipment> equipment = player.equipment();
        itemCount = equipment.size();
        ensureCapacity(itemCount);

        for (int i = 0; i < itemCount; i++) {
            IEquipment item = equipment.get(i);
            int[] req = item.requirements();
            int[] bonus = item.bonuses();

            items[i] = item;
            requirements[i] = req;
            bonuses[i] = bonus;

            int weight = 0;
            boolean reqPresent = false;
            boolean negativePresent = false;
            for (int s = 0; s < SKILLS; s++) {
                weight += bonus[s];
                reqPresent |= req[s] > 0;
                negativePresent |= bonus[s] < 0;
            }
            weights[i] = weight;
            hasRequirement[i] = reqPresent;
            hasNegativeBonus[i] = negativePresent;
        }

        SkillPoint[] skillPoints = SkillPoint.values();
        for (int s = 0; s < SKILLS; s++) {
            state[s] = player.allocated(skillPoints[s]);
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
    }

    private long activateFreeItems() {
        long mask = 0L;
        for (int i = 0; i < itemCount; i++) {
            if (hasRequirement[i] || hasNegativeBonus[i]) {
                continue;
            }

            mask |= 1L << i;
            applyBonus(i, 1);
        }
        return mask;
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

    private void search(long activeMask, long remainingMask, int count, int weight) {
        if (count > bestCount || (count == bestCount && weight > bestWeight)) {
            bestMask = activeMask;
            bestCount = count;
            bestWeight = weight;
        }

        int maximumReachableCount = count + Long.bitCount(remainingMask);
        if (maximumReachableCount < bestCount) {
            return;
        }
        if (maximumReachableCount == bestCount && weight + maskWeight(remainingMask) <= bestWeight) {
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

            long nextMask = activeMask | bit;
            if (alreadyVisited(nextMask)) {
                continue;
            }

            applyBonus(item, 1);
            if (!hasNegativeBonus[item] || activeItemsRemainValid(activeMask)) {
                search(nextMask, remainingMask & ~bit, count + 1, weight + weights[item]);
            }
            applyBonus(item, -1);
        }
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
        if (req[0] > 0 && state[0] < req[0]) return false;
        if (req[1] > 0 && state[1] < req[1]) return false;
        if (req[2] > 0 && state[2] < req[2]) return false;
        if (req[3] > 0 && state[3] < req[3]) return false;
        if (req[4] > 0 && state[4] < req[4]) return false;
        return true;
    }

    private boolean activeItemsRemainValid(long activeMask) {
        long remaining = activeMask;
        while (remaining != 0L) {
            long bit = remaining & -remaining;
            remaining ^= bit;
            int item = Long.numberOfTrailingZeros(bit);
            if (!hasRequirement[item]) {
                continue;
            }
            if (!itemStillValidWithoutOwnBonus(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean itemStillValidWithoutOwnBonus(int item) {
        int[] req = requirements[item];
        int[] bonus = bonuses[item];
        if (req[0] > 0 && state[0] < req[0] + bonus[0]) return false;
        if (req[1] > 0 && state[1] < req[1] + bonus[1]) return false;
        if (req[2] > 0 && state[2] < req[2] + bonus[2]) return false;
        if (req[3] > 0 && state[3] < req[3] + bonus[3]) return false;
        if (req[4] > 0 && state[4] < req[4] + bonus[4]) return false;
        return true;
    }

    private void applyBonus(int item, int sign) {
        int[] bonus = bonuses[item];
        state[0] += sign * bonus[0];
        state[1] += sign * bonus[1];
        state[2] += sign * bonus[2];
        state[3] += sign * bonus[3];
        state[4] += sign * bonus[4];
    }

    private Result buildResult(WynnPlayer player) {
        List<IEquipment> valid = new ArrayList<>(bestCount);
        List<IEquipment> invalid = new ArrayList<>(itemCount - bestCount);

        for (int i = 0; i < itemCount; i++) {
            if ((bestMask & (1L << i)) != 0L) {
                valid.add(items[i]);
            } else {
                invalid.add(items[i]);
            }
        }

        player.reset();
        for (int i = 0; i < valid.size(); i++) {
            player.modify(valid.get(i).bonuses(), true);
        }

        return new Result(valid, invalid);
    }
}
