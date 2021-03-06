/*
 * Copyright © 2020 LambdAurora <aurora42lambda@gmail.com>
 *
 * This file is part of LambdaControls.
 *
 * Licensed under the MIT license. For more information,
 * see the LICENSE file.
 */

package me.lambdaurora.lambdacontrols.client.gui;

import me.lambdaurora.lambdacontrols.client.controller.Controller;
import me.lambdaurora.spruceui.SpruceButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.Option;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.TranslatableText;
import org.aperlambda.lambdacommon.utils.Nameable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the option to reload the controller mappings.
 */
public class ReloadControllerMappingsOption extends Option implements Nameable
{
    private static final String KEY = "lambdacontrols.menu.reload_controller_mappings";

    public ReloadControllerMappingsOption()
    {
        super(KEY);
    }

    @Override
    public AbstractButtonWidget createButton(GameOptions options, int x, int y, int width)
    {
        SpruceButtonWidget button = new SpruceButtonWidget(x, y, width, 20, this.getName(), btn -> {
            MinecraftClient client = MinecraftClient.getInstance();
            Controller.updateMappings();
            if (client.currentScreen != null)
                client.currentScreen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
            client.getToastManager().add(new SystemToast(SystemToast.Type.TUTORIAL_HINT, new TranslatableText("lambdacontrols.controller.mappings.updated"), null));
        });
        button.setTooltip(new TranslatableText("lambdacontrols.tooltip.reload_controller_mappings"));
        return button;
    }

    @Override
    public @NotNull String getName()
    {
        return I18n.translate(KEY);
    }
}
