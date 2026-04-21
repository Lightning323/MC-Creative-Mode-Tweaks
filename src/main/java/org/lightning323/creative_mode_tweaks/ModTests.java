package org.lightning323.creative_mode_tweaks;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class ModTests {
    @GameTest(template = "minecraft:empty_3x3x3")
    public static void exampleTest(GameTestHelper helper) {

        helper.succeed();
    }
}