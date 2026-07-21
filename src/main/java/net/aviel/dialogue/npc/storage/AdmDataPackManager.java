package net.aviel.dialogue.npc.storage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.aviel.dialogue.AvielsDialogueMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads ADM content shipped inside datapacks or mod jars from
 * {@code data/<namespace>/adm_dialogues/<kind>/<path>.json}. Entries are referenced
 * from dialogues, trades and commands as {@code namespace:path}.
 */
public final class AdmDataPackManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public static final AdmDataPackManager DIALOGUES = new AdmDataPackManager("dialogues");
    public static final AdmDataPackManager TRADES = new AdmDataPackManager("trades");
    public static final AdmDataPackManager EMOTES = new AdmDataPackManager("emotes");

    private final String kind;
    private volatile Map<String, String> rawJsonById = Map.of();
    private final List<Runnable> invalidationListeners = new ArrayList<>();

    private AdmDataPackManager(String kind) {
        super(GSON, "adm_dialogues/" + kind);
        this.kind = kind;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, String> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            loaded.put(entry.getKey().toString(), entry.getValue().toString());
        }
        this.rawJsonById = Map.copyOf(loaded);
        for (Runnable listener : invalidationListeners) {
            listener.run();
        }
        if (!loaded.isEmpty()) {
            AvielsDialogueMod.LOGGER.info("Loaded {} ADM {} entr{} from datapacks", loaded.size(), kind, loaded.size() == 1 ? "y" : "ies");
        }
    }

    /** Registers a callback fired whenever datapack contents are (re)loaded. */
    public void addInvalidationListener(Runnable listener) {
        invalidationListeners.add(listener);
    }

    public String rawJson(String id) {
        return rawJsonById.get(id);
    }

    public boolean contains(String id) {
        return rawJsonById.containsKey(id);
    }

    public List<String> ids() {
        List<String> result = new ArrayList<>(rawJsonById.keySet());
        result.sort(Comparator.naturalOrder());
        return result;
    }
}
