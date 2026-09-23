package com.dndmaster.userpcagent;

import com.dndmaster.aigamemaster.localcodex.CodexWebSocketAgent;
import com.dndmaster.aigamemaster.localcodex.LocalCodexAiExecutionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

final class UserPcAgentWindow extends JFrame {
    private final JTextField relayUrl = new JTextField(UserPcAgentApplication.environmentOrDefault("RELAY_WEBSOCKET_URL", "ws://127.0.0.1:8081/ws/agent"));
    private final JPasswordField accessToken = new JPasswordField(UserPcAgentApplication.environmentOrDefault("AGENT_ACCESS_TOKEN", ""));
    private final JTextField codexExecutable = new JTextField(UserPcAgentApplication.environmentOrDefault("CODEX_EXECUTABLE", "codex"));
    private final JTextField workDirectory = new JTextField(UserPcAgentApplication.environmentOrDefault("CODEX_WORK_DIRECTORY", System.getProperty("user.home")));
    private final JLabel status = new JLabel("● 연결되지 않음");
    private final JTextArea log = new JTextArea();
    private final JButton connect = new JButton("연결 시작");
    private CodexWebSocketAgent agent;
    private LocalCodexAiExecutionPort codex;

    UserPcAgentWindow() {
        super("D&D Master · 사용자 PC 에이전트");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(720, 520);
        setLocationByPlatform(true);
        status.setForeground(new Color(160, 90, 30));
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(18, 18, 8, 18));
        addField(form, 0, "웹소켓 주소", relayUrl);
        addField(form, 1, "연결 토큰", accessToken);
        addField(form, 2, "Codex 실행 파일", codexExecutable);
        addField(form, 3, "작업 폴더", workDirectory);
        connect.addActionListener(event -> toggleConnection());
        GridBagConstraints button = constraints(4);
        button.gridx = 1;
        button.weightx = 0;
        button.fill = GridBagConstraints.NONE;
        button.anchor = GridBagConstraints.EAST;
        form.add(connect, button);

        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setBorder(BorderFactory.createEmptyBorder(16, 18, 0, 18));
        JLabel title = new JLabel("사용자 PC 에이전트");
        title.setFont(title.getFont().deriveFont(20f));
        heading.add(title, BorderLayout.WEST);
        heading.add(status, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(new JScrollPane(log), BorderLayout.SOUTH);
        ((JScrollPane) getContentPane().getComponent(2)).setPreferredSize(new java.awt.Dimension(720, 150));
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { closeAgent(); }
        });
        append("웹소켓 주소와 연결 토큰을 확인한 뒤 연결 시작을 누르세요.");
    }

    private void addField(JPanel panel, int row, String label, JTextField field) {
        GridBagConstraints labelConstraints = constraints(row);
        labelConstraints.gridx = 0;
        labelConstraints.weightx = 0;
        labelConstraints.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), labelConstraints);
        GridBagConstraints fieldConstraints = constraints(row);
        fieldConstraints.gridx = 1;
        fieldConstraints.weightx = 1;
        panel.add(field, fieldConstraints);
    }

    private GridBagConstraints constraints(int row) {
        GridBagConstraints value = new GridBagConstraints();
        value.gridy = row;
        value.insets = new Insets(5, 5, 5, 5);
        value.anchor = GridBagConstraints.WEST;
        value.fill = GridBagConstraints.HORIZONTAL;
        return value;
    }

    private void toggleConnection() {
        if (agent != null) { closeAgent(); return; }
        connect.setEnabled(false);
        status.setText("● 연결 중…");
        append("중계기에 연결하는 중입니다.");
        CompletableFuture.runAsync(() -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                codex = new LocalCodexAiExecutionPort(codexExecutable.getText().trim(), Path.of(workDirectory.getText().trim()), Duration.ofMinutes(5), mapper);
                agent = new CodexWebSocketAgent(URI.create(relayUrl.getText().trim()), new String(accessToken.getPassword()), codex, mapper);
                agent.connect().toCompletableFuture().join();
                SwingUtilities.invokeLater(() -> {
                    status.setText("● 연결됨");
                    status.setForeground(new Color(30, 125, 75));
                    connect.setText("연결 끊기");
                    connect.setEnabled(true);
                    append("연결되었습니다. 게임 서버의 실행 요청을 기다립니다.");
                });
                agent.completion().toCompletableFuture().join();
                SwingUtilities.invokeLater(() -> { status.setText("● 연결 끊김"); append("중계기 연결이 종료되었습니다."); });
            } catch (Exception error) {
                closeAgent();
                SwingUtilities.invokeLater(() -> { status.setText("● 연결 실패"); connect.setEnabled(true); append("연결 실패: " + error.getMessage()); });
            }
        });
    }

    private void closeAgent() {
        if (agent != null) agent.close();
        if (codex != null) codex.close();
        agent = null;
        codex = null;
        SwingUtilities.invokeLater(() -> { connect.setText("연결 시작"); connect.setEnabled(true); status.setText("● 연결되지 않음"); });
    }

    private void append(String message) { log.append(message + System.lineSeparator()); }
}
