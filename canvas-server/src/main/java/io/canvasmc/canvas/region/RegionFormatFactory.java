package io.canvasmc.canvas.region;

import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

import java.io.IOException;
import java.nio.file.Path;

public class RegionFormatFactory {

    // Default region format - will be configurable later
    private static EnumRegionFormat currentFormat = EnumRegionFormat.MCA;

    public static EnumRegionFormat getCurrentFormat() {
        return currentFormat;
    }

    public static void setCurrentFormat(EnumRegionFormat format) {
        currentFormat = format;
    }

    public static IRegionFile createNew(RegionStorageInfo info, Path filePath, Path folder, boolean sync) throws IOException {
        final EnumRegionFormat regionFormat = currentFormat;
        final String fullFileName = filePath.getFileName().toString();
        final String[] fullNameSplit = fullFileName.split("\\.");
        final String extensionName = fullNameSplit[fullNameSplit.length - 1];

        if (!regionFormat.getArgument().equalsIgnoreCase(extensionName)) {
            net.minecraft.server.MinecraftServer.setFatalException(new RuntimeException(
                "Invalid region file format: " + extensionName + " expected " + regionFormat.getArgument()));
            throw new IOException(
                "Invalid region file format: " + extensionName + " expected " + regionFormat.getArgument());
        }

        return regionFormat.getCreator().create(new RegionCreatorInfo(info, filePath, folder, sync));
    }

    public static String getExtensionName() {
        return "." + currentFormat.getArgument();
    }

    public interface IRegionCreateFunction {
        IRegionFile create(RegionCreatorInfo info) throws IOException;
    }

    public record RegionCreatorInfo(RegionStorageInfo info, Path filePath, Path folder, boolean sync) {
    }
}
