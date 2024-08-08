package quadric.blockvaulter;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;

public class LogUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger( LogUtils.class.getName() );
	
	public static void setSyslogLevel() {
		int logLevel = 5;
		String foo = VaultSettings.instance().getSettings().get("debugLevel");
		if(foo != null) {
			try {
				logLevel = Integer.parseInt(foo);
			} catch(Exception e) { ;}
		}
		LOGGER.debug("Will set log level to " + logLevel);
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		Configuration config = ctx.getConfiguration();
		LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME); 
		Level lvl = Level.INFO;
		switch(logLevel) {
			case 8: lvl = Level.TRACE; break;
			case 7: lvl = Level.DEBUG; break;
			case 4: lvl = Level.WARN; break;
			case 3: lvl = Level.ERROR; break;
			case 2: 
			case 1: lvl = Level.FATAL; break;
		}
		if(loggerConfig.getLevel().equals(lvl) == false) {
			loggerConfig.setLevel(lvl);
			ctx.updateLoggers();
			LOGGER.info("BlockVaulter logging level set to " + lvl.name());
		}
		
	}
}
