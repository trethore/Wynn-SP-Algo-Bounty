package com.wynncraft;

import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.IPlayer;
import com.wynncraft.core.interfaces.IPlayerBuilder;
import com.wynncraft.combination.CombinationTest;
import com.wynncraft.enums.Equipment;
import com.wynncraft.enums.EquipmentType;
import com.wynncraft.enums.SkillPoint;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class CombinationTests {

    @CombinationTest
    public void invalid_requirement_simple(IAlgorithm algorithm, IPlayerBuilder builder) {
        // Equipment that has requirements without the having the requirement
        {
            builder.equipment(Equipment.GALES_FORCE);
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 0);
        assertValid(result);
        assertInvalid(result, Equipment.GALES_FORCE);
    }

    @CombinationTest
    public void under_requirement_simple(IAlgorithm algorithm, IPlayerBuilder builder) {
        // Equipment that has requirements while ALMOST having the exactly requirement
        {
            builder.allocate(SkillPoint.AGILITY, 59);
            builder.equipment(Equipment.GALES_FORCE);
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 59);
        assertValid(result);
        assertInvalid(result, Equipment.GALES_FORCE);
    }

    @CombinationTest
    public void valid_requirement_simple(IAlgorithm algorithm, IPlayerBuilder builder) {
        // Equipment that has requirements while having exactly the requirement
        {
            builder.allocate(SkillPoint.AGILITY, 60);
            builder.equipment(Equipment.GALES_FORCE);
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 60);
        assertValid(result, Equipment.GALES_FORCE);
        assertInvalid(result);
    }

    @CombinationTest
    public void over_requirement_simple(IAlgorithm algorithm, IPlayerBuilder builder) {
        // Equipment that has requirements while having exactly the requirement
        {
            builder.allocate(SkillPoint.AGILITY, 61);
            builder.equipment(Equipment.GALES_FORCE);
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 61);
        assertValid(result, Equipment.GALES_FORCE);
        assertInvalid(result);
    }

    @CombinationTest
    public void rejects_stale_cached_result(IAlgorithm algorithm, IPlayerBuilder builder) {
        // Anti cache test rip my x1203 :(
        {
            builder.equipment(Equipment.GALES_FORCE);
        }

        IPlayer underRequirement = builder.build();
        IAlgorithm.Result firstResult = algorithm.run(underRequirement);
        assertSkillPoints(underRequirement, 0, 0, 0, 0, 0);
        assertValid(firstResult);
        assertInvalid(firstResult, Equipment.GALES_FORCE);

        builder.allocate(SkillPoint.AGILITY, 60);
        IPlayer meetsRequirement = builder.build();
        IAlgorithm.Result secondResult = algorithm.run(meetsRequirement);
        assertSkillPoints(meetsRequirement, 0, 0, 0, 0, 60);
        assertValid(secondResult, Equipment.GALES_FORCE);
        assertInvalid(secondResult);
    }

    @CombinationTest
    public void rejects_warmup_seeded_cached_result(IAlgorithm algorithm, IPlayerBuilder builder) {
        // Other anti cache test
        MutableEquipment changingItem = new MutableEquipment(
                new int[] {0, 0, 0, 0, 60},
                new int[] {0, 0, 0, 0, 0}
        );
        IEquipment[] equipment = new IEquipment[16];
        equipment[0] = changingItem;
        for (int i = 1; i < equipment.length; i++) {
            equipment[i] = new MutableEquipment(
                    new int[] {0, 0, 0, 0, 0},
                    new int[] {0, 0, 0, 0, 0}
            );
        }
        builder.equipment(equipment);

        IPlayer player = builder.build();
        IAlgorithm.Result firstResult = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 0);
        assertValid(firstResult, Arrays.copyOfRange(equipment, 1, equipment.length));
        assertInvalid(firstResult, changingItem);

        changingItem.set(
                new int[] {0, 0, 0, 0, 0},
                new int[] {0, 0, 0, 0, 5}
        );
        player.reset();

        IAlgorithm.Result secondResult = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 5);
        assertValid(secondResult, equipment);
        assertInvalid(secondResult);
    }

    @CombinationTest
    public void bootstrap_1(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.AGILITY, 84);
            builder.equipment(
                    Equipment.DIAMOND_STEAM_RING,
                    Equipment.DIAMOND_STEAM_RING,
                    Equipment.DIAMOND_STEAM_BRACELET,
                    Equipment.DIAMOND_STEAM_NECKLACE
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 84);
        assertValid(result);
        assertInvalid(result,
                Equipment.DIAMOND_STEAM_RING,
                Equipment.DIAMOND_STEAM_RING,
                Equipment.DIAMOND_STEAM_BRACELET,
                Equipment.DIAMOND_STEAM_NECKLACE
        );
    }

    @CombinationTest
    public void bootstrap_2(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.STRENGTH, 40);
            builder.allocate(SkillPoint.DEFENCE, 45);
            builder.equipment(
                    Equipment.CRYSTAL_COIL,
                    Equipment.PHAGE_PINS
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 40, 0, 0, 45, 0);
        assertValid(result);
        assertInvalid(result,
                Equipment.CRYSTAL_COIL,
                Equipment.PHAGE_PINS
        );
    }

    @CombinationTest
    public void bootstrap_3(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.AGILITY, 45);
            builder.equipment(
                    Equipment.SOARFAE,
                    Equipment.ALBEDO
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 45);
        assertValid(result);
        assertInvalid(result,
                Equipment.SOARFAE,
                Equipment.ALBEDO
        );
    }

    @CombinationTest
    public void bootstrap_4(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.STRENGTH, 37);
            builder.allocate(SkillPoint.DEXTERITY, 70);
            builder.equipment(
                    Equipment.BRAINWASH,
                    Equipment.SHATTERGLASS
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 37, 70, 0, 0, 0);
        assertValid(result);
        assertInvalid(result,
                Equipment.BRAINWASH,
                Equipment.SHATTERGLASS
        );
    }

    @CombinationTest
    public void bootstrap_self(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.AGILITY, 95);
            builder.equipment(
                    Equipment.DIAMOND_STEAM_RING,
                    Equipment.DIAMOND_STEAM_RING
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 0, 0, 0, 0, 95);
        assertValid(result);
        assertInvalid(result,
                Equipment.DIAMOND_STEAM_RING,
                Equipment.DIAMOND_STEAM_RING
        );
    }

    @CombinationTest
    public void greedy_trap_1(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.STRENGTH, 45);
            builder.allocate(SkillPoint.DEXTERITY, 45);
            builder.allocate(SkillPoint.INTELLIGENCE, 48);
            builder.allocate(SkillPoint.DEFENCE, 45);
            builder.equipment(
                    Equipment.GRANITIC_METTLE,
                    Equipment.RATION,
                    Equipment.REPURPOSED_VESSELS
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 75, 42, 55, 52, -3);
        assertValid(result,
                Equipment.GRANITIC_METTLE,
                Equipment.RATION,
                Equipment.REPURPOSED_VESSELS
        );
        assertInvalid(result);
    }

    @CombinationTest
    public void greedy_trap_2(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.STRENGTH, 43);
            builder.allocate(SkillPoint.INTELLIGENCE, 45);
            builder.equipment(
                    Equipment.SEISMOSOUL,
                    Equipment.SHATTERGLASS
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 43, 0, 45, 0, 0);
        assertValid(result);
        assertInvalid(result,
                Equipment.SEISMOSOUL,
                Equipment.SHATTERGLASS
        );
    }

    @CombinationTest
    public void greedy_trap_3(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.STRENGTH, 32);
            builder.allocate(SkillPoint.DEXTERITY, 70);
            builder.allocate(SkillPoint.INTELLIGENCE, 90);
            builder.equipment(
                    Equipment.BRAINWASH,
                    Equipment.SAPPHIRE,
                    Equipment.SHATTERGLASS
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 32, 70, 90, 0, 0);
        assertValid(result);
        assertInvalid(result,
                Equipment.BRAINWASH,
                Equipment.SAPPHIRE,
                Equipment.SHATTERGLASS
        );
    }

    @CombinationTest
    public void full_check_1(IAlgorithm algorithm, IPlayerBuilder builder) {
        {
            builder.allocate(SkillPoint.STRENGTH, 48);
            builder.allocate(SkillPoint.DEXTERITY, 47);
            builder.allocate(SkillPoint.DEFENCE, 45);
            builder.allocate(SkillPoint.AGILITY, 60);
            builder.equipment(
                    Equipment.DARKSTEEL_FULL_HELM,
                    Equipment.DARKSTEEL_CENTRIFUGE,
                    Equipment.EARTH_BREAKER,
                    Equipment.DAWNBREAK,
                    Equipment.BINDING_BRACE,
                    Equipment.DAREDEVIL
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 67, 68, -35, 68, 60);
        assertValid(result,
                Equipment.DARKSTEEL_FULL_HELM,
                Equipment.DARKSTEEL_CENTRIFUGE,
                Equipment.EARTH_BREAKER,
                Equipment.DAWNBREAK,
                Equipment.BINDING_BRACE,
                Equipment.DAREDEVIL
        );
        assertInvalid(result);
    }

    @CombinationTest
    public void negative_enables_positive(IAlgorithm algorithm, IPlayerBuilder builder) {
        // Repurposed Vessels grants +30 STR with negatives elsewhere; in
        // isolation its DEX/DEF requirements are met by allocation, and
        // its post-application state still keeps Vessels' own isValid
        // satisfied (DEX 42 ≥ 45-3, DEF 42 ≥ 45-3). Granitic Mettle
        // requires STR=75 which the player does not have until Vessels'
        // +30 STR bonus is applied; Mettle in turn adds +10 DEF which
        // strengthens Vessels' isValid further. Both items are valid.
        {
            builder.allocate(SkillPoint.STRENGTH, 45);
            builder.allocate(SkillPoint.DEXTERITY, 45);
            builder.allocate(SkillPoint.DEFENCE, 45);
            builder.equipment(
                    Equipment.GRANITIC_METTLE,
                    Equipment.REPURPOSED_VESSELS
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 75, 42, -3, 52, -3);
        assertValid(result,
                Equipment.GRANITIC_METTLE,
                Equipment.REPURPOSED_VESSELS
        );
        assertInvalid(result);
    }

    @CombinationTest
    public void cascade_blocks_self_decay(IAlgorithm algorithm, IPlayerBuilder builder) {
        // ACIDOSIS requires STR=60 and gives no bonus; player has STR=60
        // exactly. ACHROMATIC_GLOOM has no requirements but applies -3 to
        // every skill. Adding GLOOM after ACIDOSIS pushes STR to 57, which
        // breaks ACIDOSIS' isValid (state 57 < req 60 + bonus 0). The
        // cascade rule therefore rejects equipping both. Best valid set is
        // {ACIDOSIS} on a weight tiebreak (5 × 0 vs 5 × -3 = -15).
        {
            builder.allocate(SkillPoint.STRENGTH, 60);
            builder.equipment(
                    Equipment.ACIDOSIS,
                    Equipment.ACHROMATIC_GLOOM
            );
        }
        IPlayer player = builder.build();
        IAlgorithm.Result result = algorithm.run(player);
        assertSkillPoints(player, 60, 0, 0, 0, 0);
        assertValid(result, Equipment.ACIDOSIS);
        assertInvalid(result, Equipment.ACHROMATIC_GLOOM);
    }

    /**
     * Asserts all the provided equipment are inside the expected list
     *
     * @param result the result to test for
     * @param expected the expected valid equipment
     */
    private static void assertValid(IAlgorithm.Result result, IEquipment... expected) {
        assertThat(expected)
                .containsExactlyInAnyOrderElementsOf(result.valid());
    }

    /**
     * Asserts all provided equipment are inside the expected list
     *
     * @param result the result to test for
     * @param expected the expected invalid equipment
     */
    private static void assertInvalid(IAlgorithm.Result result, IEquipment... expected) {
        assertThat(expected)
                .containsExactlyInAnyOrderElementsOf(result.invalid());
    }

    /**
     * Asserts the final skill point count for the player
     *
     * @param player the player to assert for
     * @param strength the strength value
     * @param dexterity the dexterity value
     * @param intelligence the intelligence value
     * @param defence the defence value
     * @param agility the agility value
     */
    private static void assertSkillPoints(IPlayer player, int strength, int dexterity, int intelligence, int defence, int agility) {
        assertThat(player.total(SkillPoint.STRENGTH))
                .describedAs("Strength")
                .isEqualTo(strength);
        assertThat(player.total(SkillPoint.DEXTERITY))
                .describedAs("Dexterity")
                .isEqualTo(dexterity);
        assertThat(player.total(SkillPoint.INTELLIGENCE))
                .describedAs("Intelligence")
                .isEqualTo(intelligence);
        assertThat(player.total(SkillPoint.DEFENCE))
                .describedAs("Defence")
                .isEqualTo(defence);
        assertThat(player.total(SkillPoint.AGILITY))
                .describedAs("Agility")
                .isEqualTo(agility);
    }

    private static final class MutableEquipment implements IEquipment {

        private int[] requirements;
        private int[] bonuses;

        private MutableEquipment(int[] requirements, int[] bonuses) {
            set(requirements, bonuses);
        }

        private void set(int[] requirements, int[] bonuses) {
            this.requirements = requirements;
            this.bonuses = bonuses;
        }

        @Override
        public EquipmentType type() {
            return EquipmentType.ACCESSORY;
        }

        @Override
        public int[] requirements() {
            return requirements;
        }

        @Override
        public int[] bonuses() {
            return bonuses;
        }

        @Override
        public boolean hasNegativeBonus() {
            for (int bonus : bonuses) {
                if (bonus < 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canEquip(IPlayer player) {
            SkillPoint[] skills = SkillPoint.values();
            for (int i = 0; i < requirements.length; i++) {
                int requirement = requirements[i];
                if (requirement > 0 && player.total(skills[i]) < requirement) {
                    return false;
                }
            }
            return true;
        }
    }

}