package runway;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Timer;

// ==========================================
// 1. DATA ENTITIES
// ==========================================

class Flight {
    String id;
    String type; // "Landing" or "Takeoff"
    int priority;
    int size;
    int terminalNode;
    int fuel; // Percentage 0-100
    String time;
    String status; // e.g., "Waiting", "Taxiing", " docked"
    boolean isTurnaround;

    public Flight(String id, String type, int priority, int size, int terminalNode, int fuel, boolean isTurnaround) {
        this.id = id;
        this.type = type;
        this.priority = priority;
        this.size = size;
        this.terminalNode = terminalNode;
        this.time = new SimpleDateFormat("HH:mm:ss").format(new Date()); // Current System Time
        this.status = "Scheduled";
        this.fuel = fuel;
        this.isTurnaround = isTurnaround;
    }
}

class Runway {
    int id;
    int maxSizeCapacity;
    int exitNodeId;

    public Runway(int id, int maxSizeCapacity, int exitNodeId) {
        this.id = id;
        this.maxSizeCapacity = maxSizeCapacity;
        this.exitNodeId = exitNodeId;
    }
}

class Edge {
    int targetNode;
    int weight;
    public Edge(int targetNode, int weight) { this.targetNode = targetNode; this.weight = weight; }
}

class NodeDistance implements Comparable<NodeDistance> {
    int nodeId;
    int distance;
    public NodeDistance(int nodeId, int distance) { this.nodeId = nodeId; this.distance = distance; }
    @Override public int compareTo(NodeDistance other) { return Integer.compare(this.distance, other.distance); }
}

// ==========================================
// 2. BACKEND LOGIC (Graph & Scheduler)
// ==========================================

class AirportBackend {
    private Map<Integer, List<Edge>> adjList = new HashMap<>();
    private Map<Integer, String> nodeNames = new HashMap<>();
    private Map<Integer, Point> nodeCoords = new HashMap<>();
    private Map<Integer, Integer> nodeCapacity = new HashMap<>();
    private Map<Integer, Integer> nodeOccupancy = new HashMap<>();
    private boolean runway1Down = false;
    
    public void toggleRunway1Maintenance() {
        runway1Down = !runway1Down;
        
        // Find edges connected to Node 0 (Runway 1 Exit)
        // For simplicity, we assume Node 0 connects to Node 2 (Alpha)
        List<Edge> edges = adjList.get(0);
        for (Edge e : edges) {
            if (runway1Down) e.weight = Integer.MAX_VALUE; // Break it
            else e.weight = 100; // Restore original weight (Hardcoded for now)
        }
    }

    public boolean isRunway1Down() { return runway1Down; }
    
    public void setNodeCapacity(int id, int cap) {
        nodeCapacity.put(id, cap);
        nodeOccupancy.put(id, 0); // Start with 0 planes
    }

    public boolean tryAcquireGate(int nodeId) {
        int max = nodeCapacity.getOrDefault(nodeId, 0);
        int current = nodeOccupancy.getOrDefault(nodeId, 0);
        
        if (max == 0) return true; 

        if (current < max) {
            nodeOccupancy.put(nodeId, current + 1); // Lock resource
            return true;
        }
        return false; // Resource Busy
    }

    // 3. Release the Gate (Free resource)
    public void releaseGate(int nodeId) {
        int current = nodeOccupancy.getOrDefault(nodeId, 0);
        if (current > 0) {
            nodeOccupancy.put(nodeId, current - 1);
        }
    }

    public String getGateStatus(int nodeId) {
        if (!nodeCapacity.containsKey(nodeId)) return "";
        return "[" + nodeOccupancy.get(nodeId) + "/" + nodeCapacity.get(nodeId) + "]";
    }
    
    private AirportGUI gui;

    public void setGUI(AirportGUI gui) {
        this.gui = gui;
    }
    
    public void addLocation(int id, String name, int x, int y) {
        nodeNames.put(id, name);
        nodeCoords.put(id, new Point(x, y));
        adjList.putIfAbsent(id, new ArrayList<>());
    }
    
    public Map<Integer, Point> getAllCoords() { return nodeCoords; }
    public String getNodeName(int id) { return nodeNames.get(id); }
    public Map<Integer, List<Edge>> getAdjList() { return adjList; }

    public Point getNodeCoord(int id) {
        return nodeCoords.get(id);
    }

    public void addLocation(int id, String name) {
        nodeNames.put(id, name);
        adjList.putIfAbsent(id, new ArrayList<>());
    }

    public void addPath(int u, int v, int distance) {
        adjList.get(u).add(new Edge(v, distance));
        adjList.putIfAbsent(v, new ArrayList<>());
        adjList.get(v).add(new Edge(u, distance));
    }

    // Dijkstra's Algorithm
    public String findShortestPath(int startNode, int endNode) {
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>();
        Map<Integer, Integer> distances = new HashMap<>();
        Map<Integer, Integer> previousNodes = new HashMap<>();

        for (Integer node : nodeNames.keySet()) distances.put(node, Integer.MAX_VALUE);
        distances.put(startNode, 0);
        pq.add(new NodeDistance(startNode, 0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            int u = current.nodeId;
            if (u == endNode) break;
            if (current.distance > distances.get(u)) continue;

            if (adjList.containsKey(u)) {
                for (Edge edge : adjList.get(u)) {
                    int v = edge.targetNode;
                    int newDist = distances.get(u) + edge.weight;
                    if (newDist < distances.get(v)) {
                        distances.put(v, newDist);
                        previousNodes.put(v, u);
                        pq.add(new NodeDistance(v, newDist));
                    }
                }
            }
        }

        // Reconstruct Path string for display
        if (distances.get(endNode) == Integer.MAX_VALUE) return "No Path";
        
        List<String> pathNames = new ArrayList<>();
        Integer curr = endNode;
        while (curr != null) {
            pathNames.add(nodeNames.get(curr));
            curr = previousNodes.get(curr);
        }
        Collections.reverse(pathNames);
        return String.join(" -> ", pathNames);
    }
    
	 public List<Integer> getPathList(int startNode, int endNode) {
	     // Re-run Dijkstra logic to get the list
	     PriorityQueue<NodeDistance> pq = new PriorityQueue<>();
	     Map<Integer, Integer> distances = new HashMap<>();
	     Map<Integer, Integer> previousNodes = new HashMap<>();
	
	     for (Integer node : nodeNames.keySet()) distances.put(node, Integer.MAX_VALUE);
	     distances.put(startNode, 0);
	     pq.add(new NodeDistance(startNode, 0));
	
	     while (!pq.isEmpty()) {
	         NodeDistance current = pq.poll();
	         int u = current.nodeId;
	         if (u == endNode) break;
	         if (current.distance > distances.get(u)) continue;
	
	         if (adjList.containsKey(u)) {
	             for (Edge edge : adjList.get(u)) {
	                 if (edge.weight == Integer.MAX_VALUE) continue; 
	
	                 int v = edge.targetNode;
	                 int newDist = distances.get(u) + edge.weight;
	                 if (newDist < distances.get(v)) {
	                     distances.put(v, newDist);
	                     previousNodes.put(v, u);
	                     pq.add(new NodeDistance(v, newDist));
	                 }
	             }
	         }
	     }
	
	     List<Integer> path = new ArrayList<>();
	     Integer curr = endNode;
	     while (curr != null) {
	         path.add(curr);
	         curr = previousNodes.get(curr);
	     }
	     Collections.reverse(path);
	     return path;
	 }
    
}

class TrafficController {
    private PriorityQueue<Flight> flightQueue;
    private List<Runway> runways = new ArrayList<>();
    private AirportBackend backend;
    private AirportGUI gui;
    private String currentWeather = "Sunny"; // Default
    
    public void setWeather(String weather) {
        this.currentWeather = weather;
        gui.logToATC("⚠️ WEATHER ALERT: Conditions changed to " + weather.toUpperCase());
    }

    public TrafficController(AirportBackend backend, AirportGUI gui) {
        this.backend = backend;
        this.gui = gui;
        this.flightQueue = new PriorityQueue<>((f1, f2) -> {
            if (f1.priority != f2.priority) return Integer.compare(f1.priority, f2.priority);
            return Integer.compare(f2.size, f1.size);
        });
    }
    
    public void toggleMaintenance() {
        backend.toggleRunway1Maintenance();
        if (backend.isRunway1Down()) {
            gui.logToATC("ALERT: RUNWAY 1 CLOSED FOR MAINTENANCE!");
        } else {
            gui.logToATC("INFO: RUNWAY 1 REOPENED.");
        }
    }

    public void addRunway(int id, int cap, int node) {
        runways.add(new Runway(id, cap, node));
    }

    public void requestFlight(String id, String type, int prio, int size, int gate, int fuel, boolean isTurnaround) {
        Flight f = new Flight(id, type, prio, size, gate, fuel, isTurnaround);
        
        if (f.fuel < 25) {
            f.priority = 1; // Force Emergency Status
            gui.logToATC("⚠️ MAYDAY: Flight " + id + " reporting Low Fuel (" + f.fuel + "%). Priority upgraded to EMERGENCY.");
        } else {
            gui.logToATC("TOWER: Flight " + id + " requesting landing. Added to holding pattern.");
        }

        flightQueue.add(f);
        gui.addFlightRow(f.id, f.time, f.fuel + "%", "Waiting (" + type + ")", backend.getNodeName(f.terminalNode));
    }
    
    private void scheduleDeboarding(Flight f) {
        gui.logToATC("GROUND: Flight " + f.id + " de-boarding at " + backend.getNodeName(f.terminalNode));
        gui.updateFlightStatus(f.id, "De-boarding");

        javax.swing.Timer deboardTimer = new javax.swing.Timer(3000, e -> {
            backend.releaseGate(f.terminalNode);
            
            gui.logToATC("RESOURCE: Gate freed at " + backend.getNodeName(f.terminalNode) + " (Flight " + f.id + " cleared)");
            gui.updateFlightStatus(f.id, "Docked (Completed)");
            
            ((javax.swing.Timer)e.getSource()).stop();
        });
        
        deboardTimer.setRepeats(false);
        deboardTimer.start();
    }

    public void processNextFlight() {
        if (flightQueue.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No flights in queue!");
            return;
        }
        
        Flight f = flightQueue.peek();
        
        if (f.type.equals("Landing")) {
            if (!backend.tryAcquireGate(f.terminalNode)) {
                gui.logToATC("HOLDING: Flight " + f.id + " cannot land. " 
                             + backend.getNodeName(f.terminalNode) + " is FULL.");
                gui.updateFlightStatus(f.id, "Holding (Gate Full)");
                f.fuel -= 5;
            }
        }
        
        if (f.type.equals("Takeoff")) {
            backend.releaseGate(f.terminalNode);
            gui.logToATC("RESOURCE: Gate freed at " + backend.getNodeName(f.terminalNode));
        }
        
        flightQueue.poll();
        Runway assigned = null;

        if (currentWeather.equals("Stormy") && f.size == 1 && f.type.equals("Landing")) {
            gui.logToATC("NEGATIVE: Flight " + f.id + " diverted due to STORM.");
            gui.updateFlightStatus(f.id, "Diverted");
            return;
        }
        
	     Runway bestFit = null;
	     int minWastedCapacity = Integer.MAX_VALUE;
	
	     for (Runway r : runways) {
	         // 1. Must be big enough
	         if (r.maxSizeCapacity >= f.size) {
	             
	             int diff = r.maxSizeCapacity - f.size;
	             
	             if (diff < minWastedCapacity) {
	                 minWastedCapacity = diff;
	                 bestFit = r;
	             }
	         }
	     }
	     assigned = bestFit;

        if (assigned != null) {
            String path = backend.findShortestPath(assigned.exitNodeId, f.terminalNode);
            List<Integer> pathList = backend.getPathList(assigned.exitNodeId, f.terminalNode);
            
            if (gui.getMapPanel() != null) {
                gui.getMapPanel().animatePath(pathList);
            }

            gui.logToATC("CLEARED: Flight " + f.id + " landing Runway " + assigned.id);
            
            if (f.type.equals("Landing")) {
                path = backend.findShortestPath(assigned.exitNodeId, f.terminalNode);
                gui.logToATC("LANDING: " + f.id + " assigned Runway " + assigned.id);
                gui.updateFlightStatus(f.id, "Landed -> Taxiing");
                
                gui.getStatsPanel().updateStats(f.priority, true);
                
                if (f.isTurnaround) {
                    scheduleTurnaround(f);
                }
                else {
                    scheduleDeboarding(f);
                }
                
            } else {
                path = backend.findShortestPath(f.terminalNode, assigned.exitNodeId);
                gui.logToATC("DEPARTURE: " + f.id + " taking off from Runway " + assigned.id);
                gui.updateFlightStatus(f.id, "Departed");
                
                if (f.type.equals("Landing")) {
                    backend.releaseGate(f.terminalNode);
                }
                
                gui.logToATC("NEGATIVE: No runway available...");
                flightQueue.add(f);
            }
            
            gui.logToATC("ROUTING: " + f.id + " via " + path);

        } else {
            gui.logToATC("NEGATIVE: No runway for Flight " + f.id + ". Holding.");
            f.fuel -= 5;
            flightQueue.add(f);
            gui.updateFlightStatus(f.id, "Holding");
        }
    }

    private void scheduleTurnaround(Flight f) {
        gui.logToATC("GROUND: Flight " + f.id + " docked. Servicing started (5s)...");
        gui.updateFlightStatus(f.id, "Servicing (Refuel)");

        javax.swing.Timer turnTimer = new javax.swing.Timer(5000, e -> {
            gui.logToATC("PILOT: Flight " + f.id + " ready for Departure.");
            
            requestFlight(f.id, "Takeoff", 2, f.size, f.terminalNode, 100, false);
            
            ((javax.swing.Timer)e.getSource()).stop();
        });
        
        turnTimer.setRepeats(false);
        turnTimer.start();
    }
}

// ==========================================
// 3. FRONTEND (GUI)
// ==========================================

class AirportGUI extends JFrame {
    private DefaultTableModel tableModel;
    private JTable table;
    private TrafficController controller;
    private JTextArea logArea; 
    private MapPanel mapPanel;
    public void setMapPanel(MapPanel mp) { this.mapPanel = mp; }    
    private StatsPanel statsPanel;

	 public AirportGUI() {
	     setTitle("Airport Runway Management System");
	     setSize(900, 650);
	     setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	     setLayout(new BorderLayout());
	
	     String[] columns = {"Flight ID", "Time", "Fuel %", "Status", "Terminal"};
	     tableModel = new DefaultTableModel(columns, 0);
	     table = new JTable(tableModel);
	     table.setFillsViewportHeight(true);
	     table.setFont(new Font("SansSerif", Font.PLAIN, 14));
	     table.setRowHeight(25);
	     JScrollPane scrollPane = new JScrollPane(table);
	     add(scrollPane, BorderLayout.CENTER);
	
	     logArea = new JTextArea(8, 50);
	     logArea.setEditable(false);
	     logArea.setBackground(Color.BLACK);
	     logArea.setForeground(Color.GREEN);
	     logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
	     JScrollPane logScroll = new JScrollPane(logArea);
	     add(logScroll, BorderLayout.SOUTH);
	     
	     
	     statsPanel = new StatsPanel();
	     add(statsPanel, BorderLayout.WEST);
	     JPanel panel = new JPanel();
	     panel.setBorder(BorderFactory.createTitledBorder("Control Center"));
	
	     JLabel lblWeather = new JLabel("Weather:");
	     String[] weatherOptions = {"Sunny", "Rainy", "Stormy"};
	     JComboBox<String> weatherBox = new JComboBox<>(weatherOptions);
	     weatherBox.addActionListener(e -> {
	         String w = (String) weatherBox.getSelectedItem();
	         controller.setWeather(w);
	     });
	
	     JButton btnManual = new JButton("Add Custom Flight");
	     btnManual.addActionListener(e -> openManualEntryDialog());
	
	     JButton btnLoad = new JButton("Load Schedule (CSV)");
	     btnLoad.addActionListener(e -> loadCSVFile());
	
	     JButton btnProcess = new JButton("▶ Process Next");
	     btnProcess.setBackground(Color.ORANGE);
	     btnProcess.addActionListener(e -> controller.processNextFlight());
	     
	     JButton btnMaint = new JButton("⚠️ Fail Runway 1");
	     btnMaint.setBackground(Color.RED);
	     btnMaint.setForeground(Color.WHITE);

	     btnMaint.addActionListener(e -> {
	         controller.toggleMaintenance();
	         
	         if (btnMaint.getText().contains("Fail")) {
	             btnMaint.setText("Repaired Runway 1");
	             btnMaint.setBackground(Color.GREEN);
	         } else {
	             btnMaint.setText("⚠️ Fail Runway 1");
	             btnMaint.setBackground(Color.RED);
	         }
	     });
	
	     panel.add(lblWeather);
	     panel.add(weatherBox);
	     panel.add(Box.createHorizontalStrut(20));
	     panel.add(btnManual);
	     panel.add(btnLoad);
	     panel.add(Box.createHorizontalStrut(20));
	     panel.add(btnProcess);
	     panel.add(btnMaint);
	
	     add(panel, BorderLayout.NORTH);
	     

	     table.addMouseListener(new java.awt.event.MouseAdapter() {
	         @Override
	         public void mouseClicked(java.awt.event.MouseEvent evt) {
	             int row = table.rowAtPoint(evt.getPoint());
	             if (row >= 0) {
	                 String id = (String) tableModel.getValueAt(row, 0);
	                 String time = (String) tableModel.getValueAt(row, 1);
	                 String fuel = (String) tableModel.getValueAt(row, 2);
	                 String status = (String) tableModel.getValueAt(row, 3);
	                 String term = (String) tableModel.getValueAt(row, 4);

	                 String message = "✈️ FLIGHT DETAILS ✈️\n\n" +
	                                  "Flight ID: " + id + "\n" +
	                                  "Arrival Time: " + time + "\n" +
	                                  "Fuel Level: " + fuel + "\n" +
	                                  "Current Status: " + status + "\n" +
	                                  "Assigned Terminal: " + term + "\n\n" +
	                                  "-----------------------------\n" +
	                                  "Click OK to close.";

	                 JOptionPane.showMessageDialog(null, message, "Flight Manifest", JOptionPane.INFORMATION_MESSAGE);
	             }
	         }
	     });
	 }
	 
	 public StatsPanel getStatsPanel() { return statsPanel; }
	 private void openManualEntryDialog() {
	     JPanel formPanel = new JPanel(new GridLayout(0, 2));
	     JTextField idField = new JTextField("AI-");
	     
	     String[] sizes = {"1 (Small)", "2 (Medium)", "3 (Large)"};
	     JComboBox<String> sizeBox = new JComboBox<>(sizes);
	     
	     String[] priorities = {"3 (Normal)", "2 (VIP)", "1 (Emergency)"};
	     JComboBox<String> prioBox = new JComboBox<>(priorities);
	     
	     JTextField fuelField = new JTextField("50");
	     
	     String[] gates = {"Terminal 1", "Terminal 2"};
	     JComboBox<String> gateBox = new JComboBox<>(gates);
	     
	     JCheckBox turnCheck = new JCheckBox("Turnaround (Round Trip)?");
	
	     formPanel.add(new JLabel("Flight ID:")); formPanel.add(idField);
	     formPanel.add(new JLabel("Size:"));      formPanel.add(sizeBox);
	     formPanel.add(new JLabel("Priority:"));  formPanel.add(prioBox);
	     formPanel.add(new JLabel("Fuel %:"));    formPanel.add(fuelField);
	     formPanel.add(new JLabel("Gate:"));      formPanel.add(gateBox);
	     formPanel.add(new JLabel("Options:")); formPanel.add(turnCheck);
	
	     int result = JOptionPane.showConfirmDialog(null, formPanel, 
	             "Input Flight Details", JOptionPane.OK_CANCEL_OPTION);
	
	     if (result == JOptionPane.OK_OPTION) {
	         try {
	             String id = idField.getText();
	             int size = Integer.parseInt(((String)sizeBox.getSelectedItem()).substring(0,1));
	             int prio = Integer.parseInt(((String)prioBox.getSelectedItem()).substring(0,1));
	             int fuel = Integer.parseInt(fuelField.getText());
	             int gate = gateBox.getSelectedIndex() == 0 ? 4 : 5;
	             boolean isTurn = turnCheck.isSelected();
	
	             controller.requestFlight(id, "Landing", prio, size, gate, fuel, isTurn);
	         } catch (Exception ex) {
	             JOptionPane.showMessageDialog(null, "Invalid Input! Please check numbers.");
	         }
	     }
	 }
	
	 private void loadCSVFile() {
	     JFileChooser chooser = new JFileChooser();
	     chooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv", "txt"));
	     
	     int returnVal = chooser.showOpenDialog(this);
	     if(returnVal == JFileChooser.APPROVE_OPTION) {
	         File file = chooser.getSelectedFile();
	         try (BufferedReader br = new BufferedReader(new FileReader(file))) {
	             String line;
	             int count = 0;
	             while ((line = br.readLine()) != null) {
	                 String[] parts = line.split(",");
	                 if (parts.length == 5) {
	                     String id = parts[0].trim();
	                     int prio = Integer.parseInt(parts[1].trim());
	                     int size = Integer.parseInt(parts[2].trim());
	                     int gate = Integer.parseInt(parts[3].trim());
	                     int fuel = Integer.parseInt(parts[4].trim());
	                     
	                     boolean isTurn = false;
	                     if (parts.length >= 6) {
	                         isTurn = Boolean.parseBoolean(parts[5].trim()); 
	                     }
	                     
	                     controller.requestFlight(id, "Landing", prio, size, gate, fuel, isTurn);
	                     count++;
	                 }
	             }
	             logToATC("SYSTEM: Batch loaded " + count + " flights from file.");
	         } catch (Exception ex) {
	             JOptionPane.showMessageDialog(null, "Error reading file: " + ex.getMessage());
	         }
	     }
	 }

    public void setController(TrafficController c) {
        this.controller = c;
    }
    
    public MapPanel getMapPanel() {
        return mapPanel;
    }

    public void addFlightRow(String id, String time, String fuel, String status, String term) {
        tableModel.addRow(new Object[]{id, time, fuel, status, term});
    }

    public void updateFlightStatus(String id, String newStatus) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).equals(id)) {
                tableModel.setValueAt(newStatus, i, 3);
                return;
            }
        }
    }
    
    public void logToATC(String message) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        logArea.append("[" + timeStamp + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}

class StatsPanel extends JPanel {
    private JLabel lblTotal, lblEmergency, lblHoldCount;
    private int totalFlights = 0;
    private int emergencyCount = 0;
    private int holdCount = 0;

    public StatsPanel() {
        setLayout(new GridLayout(6, 1, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Live Analytics"));
        setPreferredSize(new Dimension(160, 200));
        setBackground(new Color(240, 240, 240));

        lblTotal = createLabel("Total Flights: 0");
        lblEmergency = createLabel("Emergencies: 0");
        lblHoldCount = createLabel("Current Holds: 0");

        add(lblTotal);
        add(lblEmergency);
        add(lblHoldCount);
        
        add(createLabel("----------------"));
        add(createLabel("System Status: OK"));
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        return l;
    }

    public void updateStats(int priority, boolean isHolding) {
        if (!isHolding) {
            totalFlights++;
            if (priority == 1) emergencyCount++;
        } else {
            holdCount++;
        }
        
        lblTotal.setText("Total Flights: " + totalFlights);
        lblEmergency.setText("Emergencies: " + emergencyCount);
        lblHoldCount.setText("Current Holds: " + holdCount);
        
        if (emergencyCount > 0) lblEmergency.setForeground(Color.RED);
    }
}

class MapPanel extends JPanel {
 private AirportBackend backend;
 private Map<Integer, Color> highlights = new HashMap<>();
 
 private Integer animatingNode = null; 
 private javax.swing.Timer animationTimer; 

 public MapPanel(AirportBackend backend) {
     this.backend = backend;
     this.setPreferredSize(new Dimension(450, 400)); 
     this.setBackground(new Color(30, 30, 30)); 
 }

 public void setHighlight(int nodeId, Color c) {
     highlights.put(nodeId, c);
     repaint();
 }

 public void clearHighlights() {
     highlights.clear();
     repaint();
 }
 
 public void animatePath(List<Integer> path) {
     if (path == null || path.isEmpty()) return;

     if (animationTimer != null && animationTimer.isRunning()) {
         animationTimer.stop();
     }

     final Iterator<Integer> it = path.iterator();

     animationTimer = new javax.swing.Timer(500, e -> { 
         if (it.hasNext()) {
             animatingNode = it.next();
             repaint(); 
         } else {
             ((javax.swing.Timer)e.getSource()).stop(); 
             animatingNode = null; 
             repaint();
         }
     });
     animationTimer.start();
 }

 @Override
 protected void paintComponent(Graphics g) {
     super.paintComponent(g);
     
     Graphics2D g2 = (Graphics2D) g;
     g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

     g2.setColor(Color.GRAY);
     g2.setStroke(new BasicStroke(2));

     Map<Integer, List<Edge>> adj = backend.getAdjList();
     Map<Integer, Point> coords = backend.getAllCoords();

     for (Integer u : adj.keySet()) {
         Point p1 = coords.get(u);
         if (p1 == null) continue;
         if (adj.get(u) != null) {
             for (Edge e : adj.get(u)) {
                 Point p2 = coords.get(e.targetNode);
                 if (p2 != null) g2.drawLine(p1.x, p1.y, p2.x, p2.y);
             }
         }
     }

     for (Integer id : coords.keySet()) {
         Point p = coords.get(id);
         String name = backend.getNodeName(id);
         
         Color c = Color.WHITE;
         if (name.contains("Terminal")) c = new Color(100, 150, 255);
         else if (name.contains("Runway")) c = new Color(255, 165, 0);
         
         if (highlights.containsKey(id)) {
             c = highlights.get(id);
         }
         
         drawNode(g2, p, name, c);
     }

     if (animatingNode != null) {
         Point p = backend.getAllCoords().get(animatingNode);
         if (p != null) {
             g2.setColor(Color.YELLOW);
             g2.fillOval(p.x - 12, p.y - 12, 24, 24);
             g2.setColor(Color.BLACK);
             g2.drawString("✈", p.x - 4, p.y + 5);
         }
     }
 }

 private void drawNode(Graphics2D g2, Point p, String name, Color c) {
     int radius = (c == Color.WHITE || c.getBlue() > 200 || c.getRed() > 200 && c.getGreen() < 100) ? 10 : 16;
     g2.setColor(c);
     g2.fillOval(p.x - radius, p.y - radius, radius * 2, radius * 2);
     g2.setColor(Color.LIGHT_GRAY);
     g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
     g2.drawString(name, p.x + 12, p.y + 5); 
 }
}

// ==========================================
// 4. MAIN EXECUTION
// ==========================================

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            
            AirportBackend backend = new AirportBackend();
            
            backend.addLocation(0, "Runway 1 Exit", 120, 30);
            backend.addLocation(1, "Runway 2 Exit", 280, 30);
            backend.addLocation(2, "Taxiway Alpha", 200, 120);
            backend.addLocation(3, "Taxiway Bravo", 300, 180);
            backend.addLocation(4, "Terminal 1", 100, 250);
            backend.addLocation(5, "Terminal 2", 350, 250);

            backend.addPath(0, 2, 100);
            backend.addPath(1, 2, 150);
            backend.addPath(2, 3, 50);
            backend.addPath(2, 4, 200);
            backend.addPath(3, 5, 80);
            backend.addPath(4, 5, 120);
            
            backend.setNodeCapacity(4, 2);
            backend.setNodeCapacity(5, 3);

            AirportGUI gui = new AirportGUI();
            backend.setGUI(gui);
            MapPanel mapPanel = new MapPanel(backend);
            gui.add(mapPanel, BorderLayout.EAST);
            gui.setMapPanel(mapPanel);
            gui.setSize(1100, 600);
            TrafficController atc = new TrafficController(backend, gui);
            gui.setController(atc);

            atc.addRunway(1, 3, 0);
            atc.addRunway(2, 1, 1);

            gui.setVisible(true);
        });
    }
}