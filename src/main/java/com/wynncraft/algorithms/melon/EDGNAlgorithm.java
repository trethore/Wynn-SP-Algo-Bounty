package com.wynncraft.algorithms.melon;

import com.wynncraft.core.WynnPlayer;
import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;
import com.wynncraft.enums.SkillPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Information(name = "EDGN ALGO", version = 1, authors = "EDGN")
public final class EDGNAlgorithm implements IAlgorithm<WynnPlayer> {

    private static final SkillPoint[] SP = SkillPoint.values();

    private static final int STR = 0;
    private static final int DEX = 1;
    private static final int INT = 2;
    private static final int DEF = 3;
    private static final int AGI = 4;
    private static final int SKILLS = 5;

    private final int[] state = new int[SKILLS];
    private final long[] requiredBySkill = new long[SKILLS];

    private IEquipment[] item = new IEquipment[0];
    private int[][] req = new int[0][];
    private int[][] bonus = new int[0][];
    private int[] weight = new int[0];

    private int[] positive = new int[0];
    private int[] negative = new int[0];
    private int[] negativeRank = new int[0];

    private long[] seen = new long[0];

    private int n;
    private int p;
    private int q;

    private long bestMask;
    private int bestCount;
    private int bestWeight;

    @Override
    public Result run(WynnPlayer player) {
        load(player);
        prepareSeen();

        bestMask = 0L;
        bestCount = 0;
        bestWeight = 0;

        dfs(posMask(), negMask(), 0L, 0, 0);
        return emit(player);
    }

    private void load(WynnPlayer player) {
        List<IEquipment> equipment = player.equipment();
        n = equipment.size();
        p = 0;
        q = 0;

        grow(n);
        Arrays.fill(requiredBySkill, 0L);
        loadState(player);
        split(equipment);
        sortNegative();
    }

    private void grow(int size) {
        if (item.length >= size) return;

        int cap = Math.max(32, Integer.highestOneBit(size - 1) << 1);
        item = new IEquipment[cap];
        req = new int[cap][];
        bonus = new int[cap][];
        weight = new int[cap];
        positive = new int[cap];
        negative = new int[cap];
        negativeRank = new int[cap];
    }

    private void loadState(WynnPlayer player) {
        state[STR] = player.allocated(SP[STR]);
        state[DEX] = player.allocated(SP[DEX]);
        state[INT] = player.allocated(SP[INT]);
        state[DEF] = player.allocated(SP[DEF]);
        state[AGI] = player.allocated(SP[AGI]);
    }

    private void split(List<IEquipment> equipment) {
        int i = 0;
        while (i < n) {
            loadItem(i, equipment.get(i));
            route(i);
            i++;
        }
    }

    private void loadItem(int i, IEquipment it) {
        int[] r = it.requirements();
        int[] b = it.bonuses();

        item[i] = it;
        req[i] = r;
        bonus[i] = b;
        weight[i] = b[STR] + b[DEX] + b[INT] + b[DEF] + b[AGI];

        requiredBySkill[STR] |= r[STR] > 0 ? bit(i) : 0L;
        requiredBySkill[DEX] |= r[DEX] > 0 ? bit(i) : 0L;
        requiredBySkill[INT] |= r[INT] > 0 ? bit(i) : 0L;
        requiredBySkill[DEF] |= r[DEF] > 0 ? bit(i) : 0L;
        requiredBySkill[AGI] |= r[AGI] > 0 ? bit(i) : 0L;
    }

    private void route(int i) {
        if (hasNegativeBonus(i)) {
            negativeRank[q] = rankNegative(i);
            negative[q++] = i;
            return;
        }

        positive[p++] = i;
    }

    private boolean hasNegativeBonus(int i) {
        int[] b = bonus[i];
        return b[STR] < 0 || b[DEX] < 0 || b[INT] < 0 || b[DEF] < 0 || b[AGI] < 0;
    }

    private int rankNegative(int i) {
        int[] b = bonus[i];
        int damage = min0(b[STR]) + min0(b[DEX]) + min0(b[INT]) + min0(b[DEF]) + min0(b[AGI]);
        int gain = max0(b[STR]) + max0(b[DEX]) + max0(b[INT]) + max0(b[DEF]) + max0(b[AGI]);
        return (gain << 12) + (weight[i] << 5) + damage;
    }

    private void sortNegative() {
        int i = 1;
        while (i < q) {
            insertNegative(i++);
        }
    }

    private void insertNegative(int at) {
        int itemId = negative[at];
        int rankValue = negativeRank[at];
        int i = at - 1;

        while (i >= 0 && negativeRank[i] < rankValue) {
            negative[i + 1] = negative[i];
            negativeRank[i + 1] = negativeRank[i];
            i--;
        }

        negative[i + 1] = itemId;
        negativeRank[i + 1] = rankValue;
    }

    private void prepareSeen() {
        int bits = 1 << Math.min(q, 24);
        int words = Math.max(1, (bits + 63) >>> 6);
        if (seen.length < words) seen = new long[words];
        Arrays.fill(seen, 0, words, 0L);
    }

    private void dfs(long posTodo, long negTodo, long active, int count, int totalWeight) {
        long addedPos = closePositive(posTodo);
        long nextPosTodo = posTodo ^ addedPos;
        long nextActive = active | originalPositiveMask(addedPos);
        int nextCount = count + Long.bitCount(addedPos);
        int nextWeight = totalWeight + positiveWeight(addedPos);

        record(nextActive, nextCount, nextWeight);

        if (upperBound(nextCount, nextPosTodo, negTodo) >= bestCount && markSeen(negTodo)) {
            searchNegative(nextPosTodo, negTodo, nextActive, nextCount, nextWeight);
        }

        undoPositive(addedPos);
    }

    private long closePositive(long todo) {
        long added = 0L;
        long changed;

        do {
            changed = equipablePositive(todo);
            todo ^= changed;
            added |= changed;
            applyPositive(changed, 1);
        } while (changed != 0L);

        return added;
    }

    private long equipablePositive(long mask) {
        long out = 0L;
        long m = mask;

        while (m != 0L) {
            long b = m & -m;
            int slot = Long.numberOfTrailingZeros(b);
            int id = positive[slot];
            m ^= b;
            out |= canEquip(id) ? b : 0L;
        }

        return out;
    }

    private void searchNegative(long posTodo, long negTodo, long active, int count, int totalWeight) {
        long candidates = equipableNegative(negTodo);

        while (candidates != 0L) {
            long b = candidates & -candidates;
            int slot = Long.numberOfTrailingZeros(b);
            int id = negative[slot];
            candidates ^= b;

            add(id);
            if (cascadeOk(id, active)) {
                dfs(posTodo, negTodo ^ b, active | bit(id), count + 1, totalWeight + weight[id]);
            }
            sub(id);
        }
    }

    private long equipableNegative(long mask) {
        long out = 0L;
        long m = mask;

        while (m != 0L) {
            long b = m & -m;
            int slot = Long.numberOfTrailingZeros(b);
            int id = negative[slot];
            m ^= b;
            out |= canEquip(id) ? b : 0L;
        }

        return out;
    }

    private boolean cascadeOk(int added, long active) {
        return validActive(active & impactedBy(added));
    }

    private long impactedBy(int added) {
        int[] b = bonus[added];
        long mask = 0L;

        mask |= b[STR] < 0 ? requiredBySkill[STR] : 0L;
        mask |= b[DEX] < 0 ? requiredBySkill[DEX] : 0L;
        mask |= b[INT] < 0 ? requiredBySkill[INT] : 0L;
        mask |= b[DEF] < 0 ? requiredBySkill[DEF] : 0L;
        mask |= b[AGI] < 0 ? requiredBySkill[AGI] : 0L;

        return mask;
    }

    private boolean validActive(long mask) {
        long m = mask;

        while (m != 0L) {
            long b = m & -m;
            int id = Long.numberOfTrailingZeros(b);
            m ^= b;
            if (!validWithoutSelf(id)) return false;
        }

        return true;
    }

    private boolean canEquip(int id) {
        int[] r = req[id];
        return ok(state[STR], r[STR])
                && ok(state[DEX], r[DEX])
                && ok(state[INT], r[INT])
                && ok(state[DEF], r[DEF])
                && ok(state[AGI], r[AGI]);
    }

    private boolean validWithoutSelf(int id) {
        int[] r = req[id];
        int[] b = bonus[id];
        return valid(state[STR], r[STR], b[STR])
                && valid(state[DEX], r[DEX], b[DEX])
                && valid(state[INT], r[INT], b[INT])
                && valid(state[DEF], r[DEF], b[DEF])
                && valid(state[AGI], r[AGI], b[AGI]);
    }

    private boolean ok(int have, int need) {
        return need <= 0 || have >= need;
    }

    private boolean valid(int have, int need, int self) {
        return need <= 0 || have >= need + self;
    }

    private boolean markSeen(long negTodo) {
        if (q > 24) return true;

        int idx = (int) negTodo;
        int word = idx >>> 6;
        long b = 1L << (idx & 63);
        long old = seen[word];
        seen[word] = old | b;
        return (old & b) == 0L;
    }

    private int upperBound(int count, long posTodo, long negTodo) {
        return count + Long.bitCount(posTodo) + Long.bitCount(negTodo);
    }

    private void record(long mask, int count, int totalWeight) {
        if (count < bestCount) return;
        if (count == bestCount && totalWeight <= bestWeight) return;

        bestMask = mask;
        bestCount = count;
        bestWeight = totalWeight;
    }

    private long originalPositiveMask(long posSlots) {
        long out = 0L;
        long m = posSlots;

        while (m != 0L) {
            long b = m & -m;
            int slot = Long.numberOfTrailingZeros(b);
            m ^= b;
            out |= bit(positive[slot]);
        }

        return out;
    }

    private int positiveWeight(long posSlots) {
        int sum = 0;
        long m = posSlots;

        while (m != 0L) {
            long b = m & -m;
            int slot = Long.numberOfTrailingZeros(b);
            m ^= b;
            sum += weight[positive[slot]];
        }

        return sum;
    }

    private void applyPositive(long posSlots, int sign) {
        long m = posSlots;

        while (m != 0L) {
            long b = m & -m;
            int slot = Long.numberOfTrailingZeros(b);
            m ^= b;
            apply(positive[slot], sign);
        }
    }

    private void undoPositive(long posSlots) {
        applyPositive(posSlots, -1);
    }

    private void add(int id) {
        apply(id, 1);
    }

    private void sub(int id) {
        apply(id, -1);
    }

    private void apply(int id, int sign) {
        int[] b = bonus[id];
        state[STR] += sign * b[STR];
        state[DEX] += sign * b[DEX];
        state[INT] += sign * b[INT];
        state[DEF] += sign * b[DEF];
        state[AGI] += sign * b[AGI];
    }

    private Result emit(WynnPlayer player) {
        List<IEquipment> valid = new ArrayList<>(bestCount);
        List<IEquipment> invalid = new ArrayList<>(n - bestCount);

        int i = 0;
        while (i < n) {
            ((bestMask & bit(i)) == 0L ? invalid : valid).add(item[i]);
            i++;
        }

        player.reset();
        i = 0;
        while (i < valid.size()) {
            player.modify(valid.get(i).bonuses(), true);
            i++;
        }

        return new Result(valid, invalid);
    }

    private long posMask() {
        return p == 64 ? -1L : (1L << p) - 1L;
    }

    private long negMask() {
        return q == 64 ? -1L : (1L << q) - 1L;
    }

    private long bit(int i) {
        return 1L << i;
    }

    private int max0(int x) {
        return Math.max(x, 0);
    }

    private int min0(int x) {
        return Math.min(x, 0);
    }
}