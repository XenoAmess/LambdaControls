/*
 * Copyright © 2020 LambdAurora <aurora42lambda@gmail.com>
 *
 * This file is part of LambdaControls.
 *
 * Licensed under the MIT license. For more information,
 * see the LICENSE file.
 */

package me.lambdaurora.lambdacontrols.client;

import net.minecraft.client.resource.language.I18n;
import org.aperlambda.lambdacommon.utils.Nameable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * Represents a controller type.
 *
 * @author LambdAurora
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ControllerType implements Nameable
{
    DEFAULT(0),
    DUALSHOCK(1),
    SWITCH(2),
    XBOX(3),
    STEAM(4),
    OUYA(5);

    private final int id;

    ControllerType(int id)
    {
        this.id = id;
    }

    /**
     * Returns the controller type's identifier.
     *
     * @return The controller type's identifier.
     */
    public int getId()
    {
        return this.id;
    }

    /**
     * Returns the next controller type available.
     *
     * @return The next available controller type.
     */
    public ControllerType next()
    {
        ControllerType[] v = values();
        if (v.length == this.ordinal() + 1)
            return v[0];
        return v[this.ordinal() + 1];
    }

    /**
     * Gets the translated name of this controller type.
     *
     * @return The translated name of this controller type.
     */
    public String getTranslatedName()
    {
        return I18n.translate("lambdacontrols.controller_type." + this.getName());
    }

    @Override
    public @NotNull String getName()
    {
        return this.name().toLowerCase();
    }

    /**
     * Gets the controller type from its identifier.
     *
     * @param id The identifier of the controller type.
     * @return The controller type if found, else empty.
     */
    public static Optional<ControllerType> byId(@NotNull String id)
    {
        return Arrays.stream(values()).filter(mode -> mode.getName().equalsIgnoreCase(id)).findFirst();
    }
}
