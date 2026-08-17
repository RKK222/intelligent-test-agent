package com.enterprise.testagent.localclient;

import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** 原生安装包首次启动向导；密钥只进入密码框和 0600 私有文件。 */
final class LocalClientFirstRunSetup {

    static final String PACKAGED_OPENCODE_PROPERTY = "testagent.localclient.packagedOpencodeExecutable";
    static final String DEFAULT_SERVER_URL_PROPERTY = "testagent.localclient.defaultServerUrl";
    static final String DEFAULT_WEB_URL_PROPERTY = "testagent.localclient.defaultWebUrl";
    static final String ALLOW_INSECURE_PROPERTY = "testagent.localclient.allowInsecureSetup";
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private LocalClientFirstRunSetup() {
    }

    /**
     * 仅原生安装包缺少配置时显示向导。用户取消属于正常退出，避免 launchd/systemd 反复拉起向导。
     */
    static boolean ensureConfigured() throws Exception {
        Path configDirectory = LocalClientPaths.configDirectory();
        Path configurationFile = configDirectory.resolve("client.properties");
        Path credentialFile = configDirectory.resolve("client.key");
        if (Files.isRegularFile(configurationFile) && Files.isRegularFile(credentialFile)) {
            return true;
        }

        String packagedExecutable = System.getProperty(PACKAGED_OPENCODE_PROPERTY, "").trim();
        if (packagedExecutable.isEmpty()) {
            // 命令行安装继续沿用既有错误处理，不能把任意裸 JAR 启动悄悄变成图形安装流程。
            return true;
        }
        Path opencodeExecutable = Path.of(packagedExecutable).toAbsolutePath().normalize();
        if (!Files.isExecutable(opencodeExecutable)) {
            throw new IllegalStateException("原生安装包中的 OpenCode 可执行文件不可用");
        }
        if (GraphicsEnvironment.isHeadless()) {
            // 麒麟安装脚本可能在桌面环境变量尚未导入 systemd --user 时触发；
            // 正常退出可避免 Restart=on-failure 形成无界面重启循环，用户稍后从应用菜单启动即可。
            return false;
        }

        ExistingConfiguration existing = readExistingConfiguration(configurationFile);
        SetupValues values = showSetupDialog(existing, !Files.isRegularFile(credentialFile));
        if (values == null) {
            return false;
        }
        persist(configDirectory, LocalClientPaths.stateDirectory(), opencodeExecutable, values,
                !Files.isRegularFile(configurationFile), !Files.isRegularFile(credentialFile));
        return true;
    }

    /** 将向导结果原子写入用户私有目录，供单元测试直接覆盖安全边界。 */
    static void persist(
            Path configDirectory,
            Path stateDirectory,
            Path opencodeExecutable,
            SetupValues values,
            boolean writeConfiguration,
            boolean writeCredential) throws IOException {
        try {
            ValidatedSetup validated = validate(values);
            if (writeCredential && validated.clientKey().isEmpty()) {
                throw new IllegalArgumentException("客户端密钥不能为空");
            }
            Files.createDirectories(configDirectory);
            Files.createDirectories(stateDirectory);
            if (writeConfiguration) {
                Properties properties = new Properties();
                properties.setProperty("serverUrl", normalizedBaseUri(validated.serverUri()).toString());
                properties.setProperty("webUrl", normalizedBaseUri(validated.webUri()).toString());
                properties.setProperty("opencodeExecutable", opencodeExecutable.toAbsolutePath().normalize().toString());
                properties.setProperty("opencodeConfigDirectory", configDirectory.resolve("opencode-config").toString());
                properties.setProperty("opencodeDataDirectory", stateDirectory.resolve("opencode-data").toString());
                properties.setProperty("portMin", "4096");
                properties.setProperty("portMax", "4195");
                properties.setProperty("allowInsecureControl", Boolean.toString(validated.allowInsecure()));
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                properties.store(output, "TestAgent local client configuration");
                writePrivateFile(configDirectory.resolve("client.properties"), output.toByteArray());
            }
            if (writeCredential) {
                writePrivateFile(
                        configDirectory.resolve("client.key"),
                        (validated.clientKey() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            if (writeCredential) {
                Arrays.fill(values.clientKey(), '\0');
            }
        }
    }

    private static SetupValues showSetupDialog(ExistingConfiguration existing, boolean keyRequired) throws Exception {
        AtomicReference<SetupValues> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(showSetupDialogOnEventThread(existing, keyRequired)));
        return result.get();
    }

    private static SetupValues showSetupDialogOnEventThread(ExistingConfiguration existing, boolean keyRequired) {
        String packagedDefaultServer = System.getProperty(DEFAULT_SERVER_URL_PROPERTY, "").trim();
        String defaultServer = existing.serverUrl() != null
                ? existing.serverUrl()
                : packagedDefaultServer.isEmpty() ? "https://" : packagedDefaultServer;
        String packagedDefaultWeb = System.getProperty(DEFAULT_WEB_URL_PROPERTY, "").trim();
        String defaultWeb = existing.webUrl() != null
                ? existing.webUrl()
                : packagedDefaultWeb.isEmpty() ? defaultServer : packagedDefaultWeb;
        JTextField serverField = new JTextField(defaultServer, 34);
        JTextField webField = new JTextField(defaultWeb, 34);
        JPasswordField keyField = new JPasswordField(34);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(4, 4, 4, 8);
        panel.add(new JLabel("平台服务地址"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        panel.add(serverField, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        panel.add(new JLabel("打开网页地址"), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        panel.add(webField, constraints);
        if (keyRequired) {
            constraints.gridx = 0;
            constraints.gridy++;
            constraints.fill = GridBagConstraints.NONE;
            constraints.weightx = 0;
            panel.add(new JLabel("客户端密钥"), constraints);
            constraints.gridx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1;
            panel.add(keyField, constraints);
        }

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    null,
                    panel,
                    "配置 TestAgent 本地客户端",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                Arrays.fill(keyField.getPassword(), '\0');
                return null;
            }
            char[] key = keyRequired ? keyField.getPassword() : new char[0];
            SetupValues candidate = new SetupValues(
                    serverField.getText(),
                    webField.getText(),
                    key,
                    existing.allowInsecure()
                            || Boolean.parseBoolean(System.getProperty(ALLOW_INSECURE_PROPERTY, "false")));
            try {
                validate(candidate);
                return candidate;
            } catch (IllegalArgumentException exception) {
                Arrays.fill(key, '\0');
                JOptionPane.showMessageDialog(
                        null,
                        exception.getMessage(),
                        "配置未保存",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static ExistingConfiguration readExistingConfiguration(Path configurationFile) throws IOException {
        if (!Files.isRegularFile(configurationFile)) {
            return new ExistingConfiguration(null, null, false);
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(configurationFile)) {
            properties.load(input);
        }
        return new ExistingConfiguration(
                properties.getProperty("serverUrl"),
                properties.getProperty("webUrl"),
                Boolean.parseBoolean(properties.getProperty("allowInsecureControl", "false")));
    }

    private static ValidatedSetup validate(SetupValues values) {
        URI serverUri = parseBaseUri(values.serverUrl(), "平台服务地址");
        URI webUri = parseBaseUri(values.webUrl(), "打开网页地址");
        validateScheme(serverUri, values.allowInsecure(), "平台服务地址");
        validateScheme(webUri, values.allowInsecure(), "打开网页地址");
        String clientKey = new String(values.clientKey()).trim();
        if (!clientKey.isEmpty() && (!clientKey.startsWith("tack_v1_") || clientKey.length() > 128)) {
            throw new IllegalArgumentException("客户端密钥格式不正确，请从个人设置中重新复制");
        }
        return new ValidatedSetup(serverUri, webUri, clientKey, values.allowInsecure());
    }

    private static URI parseBaseUri(String value, String displayName) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (uri.getHost() == null || uri.getRawAuthority() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(displayName + "格式不正确");
        }
    }

    private static void validateScheme(URI uri, boolean allowInsecure, String displayName) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !(allowInsecure && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(displayName + "必须使用 HTTPS");
        }
    }

    private static URI normalizedBaseUri(URI uri) {
        return URI.create(uri.getScheme() + "://" + uri.getRawAuthority());
    }

    private static void writePrivateFile(Path destination, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".testagent-", ".tmp");
        try {
            setPrivatePermissions(temporary);
            Files.write(temporary, content);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            setPrivatePermissions(destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void setPrivatePermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PRIVATE_FILE_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // 当前 macOS/麒麟均支持 POSIX 权限；保留非 POSIX 文件系统兼容。
        }
    }

    record SetupValues(String serverUrl, String webUrl, char[] clientKey, boolean allowInsecure) {
    }

    private record ExistingConfiguration(String serverUrl, String webUrl, boolean allowInsecure) {
    }

    private record ValidatedSetup(URI serverUri, URI webUri, String clientKey, boolean allowInsecure) {
    }
}
