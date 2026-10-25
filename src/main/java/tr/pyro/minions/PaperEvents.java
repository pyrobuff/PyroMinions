package tr.pyro.minions;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.*;

/** Loaded only on Paper and its derivatives; common classes use the Bukkit API. */
final class PaperEvents implements Listener {
    private final MinionListener listener;
    PaperEvents(MinionListener listener) { this.listener = listener; }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void attack(PrePlayerAttackEntityEvent event) {
        if (!listener.isMinion(event.getAttacked())) return;
        event.setCancelled(true);
        listener.pickup(event.getPlayer(), event.getAttacked());
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void rename(AsyncChatEvent event) {
        if (!listener.renames.containsKey(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        listener.rename(event.getPlayer(), PlainTextComponentSerializer.plainText().serialize(event.message()));
    }
}