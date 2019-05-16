package me.pseudoknight.chdiscord;

import com.laytonsmith.PureUtilities.DaemonManager;
import com.laytonsmith.abstraction.StaticLayer;
import com.laytonsmith.core.MSLog;
import com.laytonsmith.core.constructs.CClosure;
import com.laytonsmith.core.constructs.CInt;
import com.laytonsmith.core.constructs.Target;
import com.laytonsmith.core.environments.Environment;
import com.laytonsmith.core.environments.GlobalEnv;
import com.laytonsmith.core.exceptions.CRE.CRENotFoundException;
import com.laytonsmith.core.exceptions.ConfigRuntimeException;
import com.laytonsmith.core.exceptions.ProgramFlowManipulationException;
import com.laytonsmith.core.natives.interfaces.Mixed;
import me.pseudoknight.chdiscord.abstraction.jda.Listener;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;

import javax.security.auth.login.LoginException;
import java.util.HashMap;
import java.util.List;

public class Discord {

	public static JDA jda = null;

	static Guild guild = null;
	static HashMap<String, TextChannel> channels = new HashMap<>();

	private static Thread connection;
	private static DaemonManager dm;

	static void Connect(String token, String guildID, CClosure callback, Environment env, Target t) {
		if(jda != null) {
			Disconnect();
		}
		dm = env.getEnv(GlobalEnv.class).GetDaemonManager();
		connection = new Thread(() -> {
			try {
				jda = new JDABuilder(AccountType.BOT)
						.setToken(token)
						.setAudioEnabled(false)
						.setAutoReconnect(true)
						.addEventListener(new Listener())
						.build()
						.awaitReady();

				guild = jda.getGuildById(guildID);
				for(TextChannel channel : guild.getTextChannels()) {
					channels.put(channel.getName(), channel);
				}

			} catch(LoginException | IllegalStateException | InterruptedException ex) {
				MSLog.GetLogger().e(MSLog.Tags.RUNTIME, "Could not connect to Discord server.", t);
				dm.deactivateThread(null);
				return;
			}

			if(callback != null) {
				StaticLayer.GetConvertor().runOnMainThreadLater(dm, () -> {
					try {
						callback.execute();
					} catch (ConfigRuntimeException ex) {
						ConfigRuntimeException.HandleUncaughtException(ex, env);
					} catch (ProgramFlowManipulationException ex) {
						// do nothing
					}
				});
			}
		}, "DiscordConnect");
		dm.activateThread(connection);
		connection.start();
		StaticLayer.GetConvertor().addShutdownHook(Discord::Disconnect);
	}

	static void Disconnect() {
		if(jda != null) {
			jda.shutdownNow();
			dm.deactivateThread(connection);
		}

		jda = null;
		guild = null;
		channels.clear();
		dm = null;
		connection = null;
	}

	static Member GetMember(Mixed m, Target t) {
		Member mem;
		if(m instanceof CInt) {
			mem = guild.getMemberById(((CInt) m).getInt());
			if(mem == null) {
				throw new CRENotFoundException("A member with the id \"" + m.val() + "\" was not found on Discord server.", t);
			}
		} else {
			List<Member> mems = guild.getMembersByName(m.val(), false);
			if(mems.isEmpty()) {
				throw new CRENotFoundException("A member with the name \"" + m.val() + "\" was not found on Discord server.", t);
			}
			mem = mems.get(0);
		}
		return mem;
	}

	static Role GetRole(Mixed m, Target t) {
		Role role;
		if(m instanceof CInt) {
			role = guild.getRoleById(((CInt) m).getInt());
			if(role == null) {
				throw new CRENotFoundException("A role with the id \"" + m.val() + "\" was not found on Discord server.", t);
			}
		} else {
			List<Role> r = guild.getRolesByName(m.val(), false);
			if(r.isEmpty()) {
				throw new CRENotFoundException("A role with the name \"" + m.val() + "\" was not found on Discord server.", t);
			}
			role = r.get(0);
		}
		return role;
	}
}
