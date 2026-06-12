package io.canvasmc.canvas.region;

import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.region.linear.BufferedLinearRegionFile;
import io.canvasmc.canvas.region.linear.BufferedLinearRegionFileFlusher;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public enum EnumRegionFormat {
    MCA("mca", "mca",
        (info) -> new RegionFile(info.info(), info.filePath(), info.folder(), info.sync())),
    LINEAR_V2("linear_v2", "linear",
        (info) -> {
            final BufferedLinearRegionFileFlusher flusher = BufferedLinearRegionFileFlusher.getOrCreate(info.folder());
            final int level = GlobalConfiguration.getInstance() != null
                ? GlobalConfiguration.getInstance().regionCompressionLevel
                : 3;
            return new BufferedLinearRegionFile(info.filePath(), level, flusher);
        });

    private final String name;
    private final String argument;
    private final RegionFormatFactory.IRegionCreateFunction creator;

    EnumRegionFormat(String name, String argument, RegionFormatFactory.IRegionCreateFunction creator) {
        this.name = name;
        this.argument = argument;
        this.creator = creator;
    }

    @Nullable
    public static EnumRegionFormat fromString(String string) {
        for (EnumRegionFormat format : values()) {
            if (format.name.equalsIgnoreCase(string)) {
                return format;
            }
        }
        return null;
    }

    public RegionFormatFactory.IRegionCreateFunction getCreator() {
        return this.creator;
    }

    public String getArgument() {
        return this.argument;
    }
}
