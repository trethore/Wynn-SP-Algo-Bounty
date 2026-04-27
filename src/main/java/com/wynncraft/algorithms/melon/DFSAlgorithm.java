package com.wynncraft.algorithms.melon;

import com.wynncraft.core.WynnPlayer;
import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;
import com.wynncraft.enums.SkillPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("DuplicatedCode")
@Information(name = "DFS Algorithm", version = 1, authors = "Melon")
public final class DFSAlgorithm implements IAlgorithm<WynnPlayer> {

    private static final SkillPoint[] SP = SkillPoint.values();

    private static final int STR = 0;
    private static final int DEX = 1;
    private static final int INT = 2;
    private static final int DEF = 3;
    private static final int AGI = 4;
    private static final int SKILLS = 5;

    private int stSTR;
    private int stDEX;
    private int stINT;
    private int stDEF;
    private int stAGI;

    private final long[] requiredBySkill = new long[SKILLS];

    private IEquipment[] item = new IEquipment[0];
    private int[] reqFlat = new int[0];
    private int[] bonusFlat = new int[0];
    private int[] weight = new int[0];
    private long[] negImpact = new long[0];

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

        dfs(posMask(), negMask(), 0L, 0, 0, 0L);
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
        computeNegImpact();
        sortNegative();
    }

    private void grow(int size) {
        if (item.length >= size) return;

        int cap = Math.max(32, Integer.highestOneBit(size - 1) << 1);
        item = new IEquipment[cap];
        reqFlat = new int[cap * SKILLS];
        bonusFlat = new int[cap * SKILLS];
        weight = new int[cap];
        negImpact = new long[cap];
        positive = new int[cap];
        negative = new int[cap];
        negativeRank = new int[cap];
    }

    private void loadState(WynnPlayer player) {
        stSTR = player.allocated(SP[STR]);
        stDEX = player.allocated(SP[DEX]);
        stINT = player.allocated(SP[INT]);
        stDEF = player.allocated(SP[DEF]);
        stAGI = player.allocated(SP[AGI]);
    }

    private void split(List<IEquipment> equipment) {
        int i = 0;
        while (i < n) {
            loadItem(i, equipment.get(i));
            route(i);
            i++;
        }
    }

    private void computeNegImpact() {
        for (int qi = 0; qi < q; qi++) {
            int id = negative[qi];
            negImpact[id] = impactedBy(id);
        }
    }

    private void loadItem(int i, IEquipment it) {
        int[] r = it.requirements();
        int[] b = it.bonuses();
        int base = i * SKILLS;

        item[i] = it;
        reqFlat[base] = r[STR];
        reqFlat[base + 1] = r[DEX];
        reqFlat[base + 2] = r[INT];
        reqFlat[base + 3] = r[DEF];
        reqFlat[base + 4] = r[AGI];
        bonusFlat[base] = b[STR];
        bonusFlat[base + 1] = b[DEX];
        bonusFlat[base + 2] = b[INT];
        bonusFlat[base + 3] = b[DEF];
        bonusFlat[base + 4] = b[AGI];
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
        int base = i * SKILLS;
        return bonusFlat[base] < 0 || bonusFlat[base + 1] < 0
            || bonusFlat[base + 2] < 0 || bonusFlat[base + 3] < 0
            || bonusFlat[base + 4] < 0;
    }

    private int rankNegative(int i) {
        int base = i * SKILLS;
        int bS = bonusFlat[base], bD = bonusFlat[base + 1], bI = bonusFlat[base + 2],
            bDf = bonusFlat[base + 3], bA = bonusFlat[base + 4];
        int damage = min0(bS) + min0(bD) + min0(bI) + min0(bDf) + min0(bA);
        int gain = max0(bS) + max0(bD) + max0(bI) + max0(bDf) + max0(bA);
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

    private void dfs(long posTodo, long negTodo, long active, int count, int totalWeight, long cascadeCheck) {
        long addedPos = closePositive(posTodo);
        long nextPosTodo = posTodo ^ addedPos;

        long nextActive = active;
        int addedWeight = 0;
        long m = addedPos;
        while (m != 0L) {
            long b = m & -m;
            int slot = Long.numberOfTrailingZeros(b);
            m ^= b;
            int id = positive[slot];
            nextActive |= bit(id);
            addedWeight += weight[id];
        }
        int nextCount = count + Long.bitCount(addedPos);
        int nextWeight = totalWeight + addedWeight;

        if (validActive(cascadeCheck)) {
            record(nextActive, nextCount, nextWeight);

            if (upperBound(nextCount, nextPosTodo, negTodo) >= bestCount && markSeen(negTodo)) {
                searchNegative(nextPosTodo, negTodo, nextActive, nextCount, nextWeight);
            }
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
            addPositive(changed);
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

            addApply(id);
            dfs(posTodo, negTodo ^ b, active | bit(id), count + 1, totalWeight + weight[id], active & negImpact[id]);
            subApply(id);
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

    private long impactedBy(int added) {
        int base = added * SKILLS;
        long mask = 0L;
        mask |= bonusFlat[base] < 0 ? requiredBySkill[STR] : 0L;
        mask |= bonusFlat[base + 1] < 0 ? requiredBySkill[DEX] : 0L;
        mask |= bonusFlat[base + 2] < 0 ? requiredBySkill[INT] : 0L;
        mask |= bonusFlat[base + 3] < 0 ? requiredBySkill[DEF] : 0L;
        mask |= bonusFlat[base + 4] < 0 ? requiredBySkill[AGI] : 0L;
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
        int base = id * SKILLS;
        return ok(stSTR, reqFlat[base])
            && ok(stDEX, reqFlat[base + 1])
            && ok(stINT, reqFlat[base + 2])
            && ok(stDEF, reqFlat[base + 3])
            && ok(stAGI, reqFlat[base + 4]);
    }

    private boolean validWithoutSelf(int id) {
        int base = id * SKILLS;
        return valid(stSTR, reqFlat[base], bonusFlat[base])
            && valid(stDEX, reqFlat[base + 1], bonusFlat[base + 1])
            && valid(stINT, reqFlat[base + 2], bonusFlat[base + 2])
            && valid(stDEF, reqFlat[base + 3], bonusFlat[base + 3])
            && valid(stAGI, reqFlat[base + 4], bonusFlat[base + 4]);
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

    private void addPositive(long posSlots) {
        long m = posSlots;
        while (m != 0L) {
            long b = m & -m;
            m ^= b;
            addApply(positive[Long.numberOfTrailingZeros(b)]);
        }
    }

    private void undoPositive(long posSlots) {
        long m = posSlots;
        while (m != 0L) {
            long b = m & -m;
            m ^= b;
            subApply(positive[Long.numberOfTrailingZeros(b)]);
        }
    }

    private void addApply(int id) {
        int base = id * SKILLS;
        stSTR += bonusFlat[base];
        stDEX += bonusFlat[base + 1];
        stINT += bonusFlat[base + 2];
        stDEF += bonusFlat[base + 3];
        stAGI += bonusFlat[base + 4];
    }

    private void subApply(int id) {
        int base = id * SKILLS;
        stSTR -= bonusFlat[base];
        stDEX -= bonusFlat[base + 1];
        stINT -= bonusFlat[base + 2];
        stDEF -= bonusFlat[base + 3];
        stAGI -= bonusFlat[base + 4];
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
