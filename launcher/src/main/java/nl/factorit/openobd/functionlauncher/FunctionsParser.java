package nl.factorit.openobd.functionlauncher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Parses a json file containing all openOBD function this Function Launcher should server. File setup is basically identical to the
 * original script.json file used by the Scripting engine. With a couple of new elements.
 *
 * [
 *   "36af611a-832e-40dd-9946-a5dd24b4c0b9": {
 *     "signature": "0800fc577294c34e0b28ad2839435945",
 *     "name": "SCRIPT:EXAMPLE:01",
 *     "description": "Example: openOBD function",
 *     "version": "1.0",
 *     "author": "WP van Tunen",
 *     "executor": "python",
 *     "runtime": "1",
 *     "mode": 0,
 *     "mode_string": "UNDEFINED"
 *   }
 * ]
 *
 * Extra fields will be ignored.
 */
public class FunctionsParser {
    private static final Logger logger = Logger.getLogger("FunctionsParser");

    private final int minimumMode;
    private final Map<UUID, FunctionDescription> functions = new HashMap<>();

    /**
     * Parses the configured json file containing the openOBD function descriptions. Functions can be filtered by mode
     * @throws IOException
     */
    public FunctionsParser() throws IOException {
        // Path to the 'index' file containing all functions with basic info like code path and name (comparable to script.json in the script engine)
        String filename = System.getenv("FUNCTIONS_FILE_LOCATION");
        String minimumMode = System.getenv("FUNCTIONS_MINIMUM_MODE");

        logger.debug("Using %s as functions file".formatted(filename));

        FunctionMode mode;
        try {
            mode = FunctionMode.valueOf(minimumMode);
        } catch (Exception e) {
            mode = FunctionMode.UNDEFINED;
        }

        this.minimumMode = mode.label;

        logger.info("Loading functions with at least mode: %s".formatted(mode));

        TypeReference<HashMap<UUID, FunctionDescription>> functionsType = new TypeReference<>() {};

        // Map functions described the .json file to a Java object, with a small filter on the mode the function is set to
        new ObjectMapper().readValue(Paths.get(filename).toFile(), functionsType).forEach((uuid, description) -> {
            if (this.minimumMode <= description.mode.label) {
                this.functions.put(uuid, description);

                logger.debug("Found function %s(%s) version %s".formatted(uuid, description.name, description.version));
            } else {
                logger.debug("Skipping function %s(%s) version %s, due to minimum mode (is had %s)".formatted(
                        uuid,
                        description.name,
                        description.version,
                        description.mode
                ));
            }
        });

        logger.info("Loaded %d function(s)".formatted(this.functions.size()));
    }

    public Map<UUID, FunctionDescription> getFunctions() {
        return this.functions;
    }

    public enum FunctionMode {
        UNDEFINED(0),
        DEVELOPMENT(1),
        ALPHA(2),
        BETA(3),
        RELEASE_CANDIDATE(4),
        STABLE(5),
        DEPRECATED(6);

        public final int label;

        FunctionMode(int label) {
            this.label = label;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FunctionDescription(
            @JsonProperty("name") String name,
            @JsonProperty("signature") String signature,

            @JsonProperty("description") String description,
            @JsonProperty("version") String version,
            @JsonProperty("author") String author,
            @JsonProperty("executor") String executor,
            @JsonProperty("runtime") String runtime,
            @JsonProperty("mode") FunctionMode mode
    ) {
    }
}
