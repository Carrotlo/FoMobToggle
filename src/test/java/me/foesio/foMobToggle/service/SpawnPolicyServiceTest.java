package me.foesio.foMobToggle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.foesio.foMobToggle.model.ToggleCategory;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class SpawnPolicyServiceTest {

    @Test
    void typePreviewClassifiesLikeTheLiveMobInterfaces() {
        assertEquals(ToggleCategory.MONSTERS, SpawnPolicyService.classify(EntityType.ZOMBIE.getEntityClass()));
        assertEquals(ToggleCategory.MONSTERS, SpawnPolicyService.classify(EntityType.BREEZE.getEntityClass()));
        assertEquals(ToggleCategory.PASSIVE, SpawnPolicyService.classify(EntityType.COW.getEntityClass()));
        assertEquals(ToggleCategory.PASSIVE, SpawnPolicyService.classify(EntityType.BAT.getEntityClass()));
        assertEquals(ToggleCategory.PASSIVE, SpawnPolicyService.classify(EntityType.SQUID.getEntityClass()));
        assertEquals(ToggleCategory.PASSIVE, SpawnPolicyService.classify(EntityType.ALLAY.getEntityClass()));
        assertEquals(ToggleCategory.PASSIVE, SpawnPolicyService.classify(EntityType.IRON_GOLEM.getEntityClass()));
        assertNull(SpawnPolicyService.classify(EntityType.ARMOR_STAND.getEntityClass()));
    }
}
