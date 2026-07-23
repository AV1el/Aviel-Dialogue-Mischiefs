package net.aviel.dialogue;

import com.mojang.logging.LogUtils;
import net.aviel.dialogue.command.DialogueNpcCommand;
import net.aviel.dialogue.command.NpcSelections;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.network.NpcDialogueAnimationPacket;
import net.aviel.dialogue.network.NpcDialogueChoicePacket;
import net.aviel.dialogue.network.NpcEditorActionPacket;
import net.aviel.dialogue.network.NpcMoveToPacket;
import net.aviel.dialogue.network.OpenNpcEditorPacket;
import net.aviel.dialogue.network.NpcEmoteSyncPacket;
import net.aviel.dialogue.network.NpcTradeActionPacket;
import net.aviel.dialogue.network.OpenNpcDialoguePacket;
import net.aviel.dialogue.network.OpenNpcTradePacket;
import net.aviel.dialogue.npc.DialogueSessionManager;
import net.aviel.dialogue.npc.NpcTradeService;
import net.aviel.dialogue.npc.storage.AdmDataPackManager;
import net.aviel.dialogue.npc.storage.ConfigAssetPackBuilder;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

@Mod(AvielsDialogueMod.MODID)
public class AvielsDialogueMod {
    public static final String MODID = "adm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Places the points NPCs walk to; see {@link net.aviel.dialogue.item.PoiMarkerItem}. */
    public static final DeferredHolder<net.minecraft.world.item.Item, net.minecraft.world.item.Item> POI_MARKER =
            ITEMS.register("poi_marker", () -> new net.aviel.dialogue.item.PoiMarkerItem(
                    new net.minecraft.world.item.Item.Properties().stacksTo(1)));

    public static final DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> TAB =
            CREATIVE_TABS.register("adm", () -> net.minecraft.world.item.CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.adm"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(POI_MARKER.get()))
                    .displayItems((parameters, output) -> output.accept(POI_MARKER.get()))
                    .build());

    public static final DeferredHolder<EntityType<?>, EntityType<DialogueNpcEntity>> DIALOGUE_NPC = ENTITY_TYPES.register(
            "dialogue_npc",
            () -> EntityType.Builder.of(DialogueNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(id("dialogue_npc").toString())
    );

    public AvielsDialogueMod(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, Config.SPEC);
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::addPackFinders);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerAttributes);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        AdmDataPackManager.DIALOGUES.addInvalidationListener(DialogueRepository::invalidateCaches);
        AdmDataPackManager.TRADES.addInvalidationListener(NpcTradeService::invalidateCaches);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MODID).versioned("1");
        registrar.playToClient(OpenNpcDialoguePacket.TYPE, OpenNpcDialoguePacket.STREAM_CODEC, OpenNpcDialoguePacket::handle);
        registrar.playToServer(NpcDialogueChoicePacket.TYPE, NpcDialogueChoicePacket.STREAM_CODEC, NpcDialogueChoicePacket::handle);
        registrar.playToClient(OpenNpcTradePacket.TYPE, OpenNpcTradePacket.STREAM_CODEC, OpenNpcTradePacket::handle);
        registrar.playToServer(NpcTradeActionPacket.TYPE, NpcTradeActionPacket.STREAM_CODEC, NpcTradeActionPacket::handle);
        registrar.playToClient(NpcEmoteSyncPacket.TYPE, NpcEmoteSyncPacket.STREAM_CODEC, NpcEmoteSyncPacket::handle);
        registrar.playToServer(NpcDialogueAnimationPacket.TYPE, NpcDialogueAnimationPacket.STREAM_CODEC, NpcDialogueAnimationPacket::handle);
        registrar.playToClient(OpenNpcEditorPacket.TYPE, OpenNpcEditorPacket.STREAM_CODEC, OpenNpcEditorPacket::handle);
        registrar.playToServer(NpcEditorActionPacket.TYPE, NpcEditorActionPacket.STREAM_CODEC, NpcEditorActionPacket::handle);
        registrar.playToServer(NpcMoveToPacket.TYPE, NpcMoveToPacket.STREAM_CODEC, NpcMoveToPacket::handle);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        DialogueNpcCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(AdmDataPackManager.DIALOGUES);
        event.addListener(AdmDataPackManager.TRADES);
        event.addListener(AdmDataPackManager.EMOTES);
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DialogueSessionManager.closeAllFor(event.getEntity().getUUID());
        NpcSelections.forget(event.getEntity().getUUID());
    }

    private void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path packRoot = ConfigAssetPackBuilder.prepare();
        PackLocationInfo location = new PackLocationInfo(
                "adm/config_assets",
                Component.literal("ADM Config Assets"),
                PackSource.DEFAULT,
                Optional.empty()
        );
        Pack pack = Pack.readMetaAndCreate(
                location,
                new PathPackResources.PathResourcesSupplier(packRoot),
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DIALOGUE_NPC.get(), DialogueNpcEntity.createAttributes().build());
    }
}
