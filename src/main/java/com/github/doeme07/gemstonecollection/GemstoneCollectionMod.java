package com.github.doeme07.gemstonecollection;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod(modid = GemstoneCollectionMod.MOD_ID, name = "Gemstone Collection Tracker", version = "1.0")
public class GemstoneCollectionMod {
    public static final String MOD_ID = "gemstonecollection";
    private static final String API_KEY = "TOKEN"; // Replace with your Hypixel API Key
    private static final String UUID = "7ebb8655-e5a4-4f97-88ff-f93647f8cc86"; // Replace with the UUID of the player to track

    private static final int FETCH_INTERVAL = 2400; // Fetch data every 2 minutes (1200 ticks per minute)
    private static int tickCounter = 0;

    private static boolean trackingEnabled = false; // Toggle for tracking
    private static final Set<String> trackedCollections = new HashSet<>(); // Stores collections to track
    private static final Map<String, Long> collectionCounts = new HashMap<>(); // Stores collection values

    public GemstoneCollectionMod() {
        System.out.println("Skyblock Gemstone Collection Mod Loaded!");
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        System.out.println("Pre-Initialization");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("Initialization");
        MinecraftForge.EVENT_BUS.register(this);
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new TrackCommand());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        System.out.println("Post-Initialization");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().theWorld == null) {
            return;
        }

        if (!trackingEnabled || trackedCollections.isEmpty()) {
            return; // Do nothing if tracking is disabled or no collections are tracked
        }

        tickCounter++;

        // **Only fetch data every 2 minutes (1200 ticks)**
        if (tickCounter >= FETCH_INTERVAL) {
            tickCounter = 0; // Reset counter
            fetchCollectionData();
        }
    }

    private void fetchCollectionData() {
        System.out.println("[GemstoneCollectionMod] Fetching Collection Data for: " + trackedCollections);

        try {
            String targetUrl = "https://api.hypixel.net/v2/skyblock/profiles?uuid=" + UUID;
            URL url = new URL(targetUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("API-Key", API_KEY);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            System.out.println("[GemstoneCollectionMod] HTTP Response Code: " + responseCode);

            if (responseCode != 200) {
                System.err.println("[GemstoneCollectionMod] API request failed!");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String responseBody = response.toString();

            // **Update tracked collections**
            for (String collection : trackedCollections) {
                collectionCounts.put(collection, extractCollectionValue(responseBody, collection));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long extractCollectionValue(String responseBody, String collectionName) {
        // **Find the "collection" object first**
        Pattern collectionPattern = Pattern.compile("\"collection\"\\s*:\\s*\\{(.*?)\\}");
        Matcher collectionMatcher = collectionPattern.matcher(responseBody);

        if (collectionMatcher.find()) {
            String collectionData = collectionMatcher.group(1); // Extract only the content inside "collection"

            // **Now search for the specific collection key within the extracted object**
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(collectionName) + "\"\\s*:\\s*(\\d+)");
            Matcher matcher = pattern.matcher(collectionData);

            if (matcher.find()) {
                return Long.parseLong(matcher.group(1)); // Return the found number
            }
        }

        System.out.println("[GemstoneCollectionMod] " + collectionName + " not found in response.");
        return 0;
    }
    // **Format the number dynamically with single quotes (e.g., 222 -> "222", 2222222 -> "2'222'222")**
    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.2f B", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.2f M", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.2f K", number / 1_000.0);
        } else {
            return String.valueOf(number);
        }
    }

    // **Display the Formatted Collection Counts on the Screen**
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && trackingEnabled && !trackedCollections.isEmpty()) {
            int screenWidth = mc.displayWidth / 2;
            int screenHeight = mc.displayHeight / 2;
            int yOffset = -30; // Start above crosshair

            for (String collection : trackedCollections) {
                String displayText = collection + ": " + formatNumber(collectionCounts.getOrDefault(collection, 0L));

                // **Draw to the left of the crosshair**
                mc.fontRendererObj.drawStringWithShadow(
                        displayText,
                        screenWidth / 2 - 50, // Offset left
                        screenHeight / 2 + yOffset,
                        0xFF00FF // Pink color
                );

                yOffset += 10; // Move down for next line
            }
        }
    }

    // **Command Class for Toggling Collection Tracking**
    public static class TrackCommand extends CommandBase {
        @Override
        public String getCommandName() {
            return "track";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/track <collection_name> - Toggles tracking for a specific collection\n" +
                    "/track remove <collection_name> - Removes a specific collection from tracking\n" +
                    "/track list - Lists all tracked collections";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length == 0) {
                trackingEnabled = !trackingEnabled;
                sender.addChatMessage(new ChatComponentText("[GemstoneCollectionMod] Tracking " + (trackingEnabled ? "enabled" : "disabled") + "."));
                return;
            }

            String action = args[0].toLowerCase();

            // **Handle "/track remove <collection_name>"**
            if (action.equals("remove") && args.length > 1) {
                String collectionToRemove = args[1].toUpperCase();

                if (trackedCollections.contains(collectionToRemove)) {
                    trackedCollections.remove(collectionToRemove);
                    sender.addChatMessage(new ChatComponentText("[GemstoneCollectionMod] Stopped tracking: " + collectionToRemove));
                } else {
                    sender.addChatMessage(new ChatComponentText("[GemstoneCollectionMod] " + collectionToRemove + " is not currently being tracked."));
                }
                return;
            }

            // **Handle "/track list"**
            if (action.equals("list")) {
                if (trackedCollections.isEmpty()) {
                    sender.addChatMessage(new ChatComponentText("[GemstoneCollectionMod] No collections are currently being tracked."));
                } else {
                    sender.addChatMessage(new ChatComponentText("[GemstoneCollectionMod] Currently tracking: " + String.join(", ", trackedCollections)));
                }
                return;
            }

            // **Handle "/track <collection_name>" (toggle tracking)**
            String collection = args[0].toUpperCase();
            if (trackedCollections.contains(collection)) {
                trackedCollections.remove(collection);
                sender.addChatMessage(new ChatComponentText("[GemstoneCollectionMod] Stopped tracking: " + collection));
            } else {
                trackedCollections.add(collection);
                tickCounter = FETCH_INTERVAL + 1;
                trackingEnabled = true;
                sender.addChatMessage(new ChatComponentText("[GemstoneCollectionMod] Now tracking: " + collection));
            }
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0; // Allows all players to use the command
        }
    }
}
