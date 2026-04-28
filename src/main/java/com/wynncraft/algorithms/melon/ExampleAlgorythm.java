package com.wynncraft.algorithms.melon;

import com.wynncraft.core.interfaces.IAlgorithm;
import com.wynncraft.core.interfaces.IEquipment;
import com.wynncraft.core.interfaces.Information;

import java.util.Collections;
import java.util.List;

/**
 * Example algorithm template.
 *
 * <p>This class is intentionally not registered in {@code AlgorithmRegistry}.
 */
@Information(name = "Example Algorythm", version = 1, authors = "Melon")
public final class ExampleAlgorythm implements IAlgorithm<ExamplePlayer> {

    @Override
    public Result run(ExamplePlayer player) {
        List<IEquipment> equipment = player.equipment();
        return new Result(Collections.emptyList(), equipment);
    }
}
