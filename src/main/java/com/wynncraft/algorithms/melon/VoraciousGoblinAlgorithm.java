package com.wynncraft.algorithms.melon;

import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.Information;

import java.util.Collections;

/**
 * Voracious Goblin algorithm.
 *
 * @author Melon Team (riege and trethore)
 * @version 3
 */
@SuppressWarnings("DuplicatedCode")
@Information(name = "Voracious Goblin", version = 3, authors = "Melon")
public final class VoraciousGoblinAlgorithm implements IAlgorithm<VoraciousPlayer> {

    @Override
    public Result run(VoraciousPlayer player) {
        return new Result(Collections.emptyList(), player.equipment());
    }
}
