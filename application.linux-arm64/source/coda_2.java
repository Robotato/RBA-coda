import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import controlP5.*; 
import org.gicentre.utils.spatial.*; 
import org.gicentre.utils.network.*; 
import org.gicentre.utils.network.traer.physics.*; 
import org.gicentre.utils.geom.*; 
import org.gicentre.utils.move.*; 
import org.gicentre.utils.stat.*; 
import org.gicentre.utils.gui.*; 
import org.gicentre.utils.colour.*; 
import org.gicentre.utils.text.*; 
import org.gicentre.utils.*; 
import org.gicentre.utils.network.traer.animation.*; 
import org.gicentre.utils.io.*; 
import java.util.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class coda_2 extends PApplet {















class Person extends Node {
    public float identity;
    boolean infected;
    
    public Person(float x, float y, float identity) {
        super(x, y);
        this.identity = identity;
    }
    
    public void draw(processing.core.PApplet applet, float px, float py) {
        // Selection status
        if (selection.contains(this)) {
            stroke(color(50, 100, 230));
            strokeWeight(3);
        }
        
        // fill color based on identity
        fill(lerpColor(0xff4d44d5,
                       0xffed6d27,
                       identity));

        // draw the dang thing!
        ellipse(px, py, 25, 25);
        
        // draw threshold, neighbor information
        fill(color(255, 255, 255, 100));
        arc(px, py, 25, 25, 1.5f * PI, lerp(1.5f*PI, 1.5f*PI + 2*PI, getPropInfectedNeighbors()));
        stroke(0xffff0050);
        strokeWeight(1);
        float radius = 25 / 2.f;
        float thresholdAngle = lerp(1.5f*PI, 1.5f*PI + 2*PI, threshold);
        line(px, py, px + cos(thresholdAngle)*radius, py + sin(thresholdAngle)*radius);
        
        // infection status
        if (infected) {
            fill(0xffff0050);
            ellipse(px, py, 10, 10);
        }
        
        noStroke();
    }
    
    public HashSet<Person> getNeighbors() {
        HashSet<Person> neighbors = new HashSet<Person>();
        
        for (Edge e : getInEdges()) {
            Person neighbor = (Person)(e.getNode1().equals(this) ? e.getNode2()
                                                                 : e.getNode1());
            neighbors.add(neighbor);
        }
        
        return neighbors;
    }
    
    public float getPropInfectedNeighbors() {
        HashSet<Person> neighbors = getNeighbors();
        
        float infectedNeighbors = 0;
        for (Person neighbor : neighbors) {
            if (neighbor.infected) { infectedNeighbors++; }
        }
        
        return infectedNeighbors / neighbors.size();
    }
}

ControlP5 cp5;
int lastGuiInteraction = 0;
final int GUI_IMMUNITY = 7;
ArrayDeque<Person> selection;

float nextIdentity = 1;
float threshold = 0.5f;
float xenophilia = 0.5f;
float stochasticity = 0.1f;

enum Mode { AddNode, RemoveNode, Connect, Disconnect, Infect, Select, Run }
Mode mode;
int lastStepFrame = 0;
final int STEPINTERVAL = 100;

MyParticleViewer<Person, Edge> viewer;
static float selectionRadius = 100;

public void setup() {
    
    
    cp5 = new ControlP5(this);
    
    cp5.addButton("clearbtn")
        .setPosition(0, 0)
        .setSize(200, 100)
        ;
    cp5.addSlider("nextIdentitySlider")
        .setPosition(0, 100)
        .setSize(200, 30)
        .setRange(0, 100)
        .setValue(100);
        ;
    cp5.addSlider("thresholdSlider")
        .setPosition(0, 135)
        .setSize(200, 30)
        .setRange(0, 100)
        .setValue(50);
        ;
    cp5.addSlider("xenophiliaSlider")
        .setPosition(0, 170)
        .setSize(200, 30)
        .setRange(0, 100)
        .setValue(25)
        ;
    cp5.addSlider("stochasticitySlider")
        .setPosition(0, 210)
        .setSize(200, 30)
        .setRange(0, 100)
        .setValue(10)
        ;
    
    selection = new ArrayDeque<Person>();
    
    mode = Mode.Select;
    
    viewer = new MyParticleViewer<Person, Edge>(this, width, height);
    
    Person node1 = new Person(100, 100, nextIdentity);
    Person node2 = new Person(300, 300, nextIdentity);
    Edge edge12 = new Edge(node1, node2);
    viewer.addNode(node1);
    viewer.addNode(node2);
    viewer.addEdge(edge12);
}

public void draw() {
    background(150);
    
    fill(0);
    textSize(30);
    textAlign(LEFT);
    text("Mode: " + mode.toString(), 10, height - 30);
    textAlign(CENTER);
    text("(1/a): Add Node   (2/r): Remove Node   (3/c): Connect   (4/d): Disconnect   (i/5): Infect   (SPACE): Select", width / 2, 30);
    text("(x): Generate connections   (TAB): Space out nodes   (s): Run one step   (ENTER): Play/Pause", width / 2, 70);
    
    viewer.resetView();
    viewer.draw();
    
    fill(color(230, 75, 50, 100));
    ellipse(mouseX, mouseY, 100, 100);
    
    if (mode == Mode.Run) {
        if (frameCount - lastStepFrame > STEPINTERVAL) {
            runStep();
            lastStepFrame = frameCount;
        }
        
        //draw progress bar
        fill(color(25, 125, 175));
        noStroke();
        rect(0, height - 10, map(frameCount - lastStepFrame, 0, STEPINTERVAL, 0, width), 10);
    }
}

public void generateConnections() {
    for (Person p1 : viewer.getNodes()) {
        for (Person p2 : viewer.getNodes()) {
            if (!p1.equals(p2) && viewer.getEdge(p1, p2) == null) {
                float difference = abs(p1.identity - p2.identity);
                
                if (random(1) < max(0, xenophilia - difference)) {
                    viewer.addEdge(new Edge(p1, p2));
                }
            }
        }
    }
}

public void runStep() {
    HashSet<Person> newlyInfectedSet = new HashSet<Person>();
    
    for (Person p : viewer.getNodes()) {
        if (p.getPropInfectedNeighbors() >= threshold) {
            newlyInfectedSet.add(p);
        }
    }
    
    for (Person p : newlyInfectedSet) {
        p.infected = true;
    }
}

public void controlEvent(ControlEvent event) {
  lastGuiInteraction = frameCount;
}

public void keyPressed() {
    switch(key) {
        case ' ':
            mode = Mode.Select;
            selection.clear();
            break;
        case 'a':
        case '1':
            mode = Mode.AddNode;
            selection.clear();
            break;
        case 'r':
        case '2':
            mode = Mode.RemoveNode;
            selection.clear();
            break;
        case 'c':
        case '3':
            mode = Mode.Connect;
            selection.clear();
            break;
        case 'd':
        case '4':
            mode = Mode.Disconnect;
            selection.clear();
            break;
        case 'i':
        case '5':
            mode = Mode.Infect;
            selection.clear();
            break;
        case 'x':
            generateConnections();
            break;
        case 's':
            runStep();
            break;
        case ENTER:
            mode = (mode == Mode.Run ? Mode.Select
                                     : Mode.Run);
            lastStepFrame = frameCount;
            selection.clear();
            break;
        case TAB:
            viewer.spaceNodes();
            break;
    }
}

public void mousePressed() {
    //GUI Immunity
    if (frameCount - lastGuiInteraction < GUI_IMMUNITY) { return; }
    
    Person nearest = viewer.getNearest(mouseX, mouseY, selectionRadius);
    
    switch(mode) {
        case AddNode:
            PVector mousePos = viewer.getMousePosition();
            nextIdentity = min(1, max(0, nextIdentity + random(-.5f*stochasticity, .5f*stochasticity)));
            Person p = new Person(mousePos.x, mousePos.y, nextIdentity);
            viewer.addNode(p);
            break;
            
        case RemoveNode:
            if (nearest != null) {
                viewer.removeNode(nearest);
            }
            break;
            
        case Connect:
        case Disconnect: // shared selection logic
        
            if (nearest == null) { break; }        
        
            if (selection.peek() != null && selection.peek().equals(nearest)) { // unselect if twice clicked
                    selection.poll();
            }
            else {
                selection.add(nearest);
                
                if (selection.size() == 2) { // get ready to do the thing!
                    Person p1 = selection.poll();
                    Person p2 = selection.poll();
                    
                    Edge existingEdge = viewer.getEdge(p1, p2);
                    
                    if (mode == Mode.Connect && existingEdge == null) {
                        viewer.addEdge(new Edge(p1, p2));
                    }
                    else if (mode == Mode.Disconnect && existingEdge != null) {
                        viewer.removeEdge(existingEdge);
                    }
                }
            }
            break;
        
        case Infect:
            if (nearest != null) {
                nearest.infected = !nearest.infected;
            }
            break;
        
        case Select:
            if (viewer.getNearest(mouseX, mouseY, selectionRadius) != null) {
                viewer.selectNearestWithMouse();
            }
            break;
            
        case Run: //do nothing
            break;
    }
}

public void mouseReleased(){
    viewer.dropSelected();
}

public void clearbtn(int value) {
    viewer = new MyParticleViewer<Person, Edge>(this, width, height);
    viewer.addNode(new Person(100, 100, nextIdentity));
}

public void nextIdentitySlider(float value) {
    nextIdentity = value / 100.f;
}

public void thresholdSlider(float value) {
    threshold = value / 100.f;
}

public void xenophiliaSlider(float value) {
    xenophilia = value / 100.f;
}

public void stochasticitySlider(float value) {
    stochasticity = value / 100.f;
}


// *****************************************************************************************
/** Allows particles to be viewed and animated. Suitable for spring embedded / force directed
 *  layouts for arranging networks and other collections of interacting objects. Uses the 
 *  physics engine and animation smoothers developed by Jeffrey Traer Bernstein.
 *  @param <N> Type of node to be stored in the particle viewer. This can be a <code>Node</code>
 *             or any specialised subclass of it.
 *  @param <E> Type of edge to be stored in the particle viewer. This can be an <code>Edge</code>
 *             or any specialised subclass of it.
 *  @author Jo Wood, giCentre, City University London.
 *  @version 3.4, 5th February, 2016. 
 */ 
// *****************************************************************************************

/* This file is part of giCentre utilities library. gicentre.utils is free software: you can 
 * redistribute it and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * gicentre.utils is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this
 * source code (see COPYING.LESSER included with this source code). If not, see 
 * http://www.gnu.org/licenses/.
 */

public class MyParticleViewer<N extends Node, E extends Edge>
{
    // ----------------------------- Object variables ------------------------------

    private PApplet parent;                         // Processing applet that controls the drawing.
    private ParticleSystem physics;              // The environment for particle modelling.
    private Smoother3D centroid;                 // For smooth camera centring.
    private int width, height;                     // Dimensions of the drawable area.
    private boolean isPaused;                     // Controls whether or not the particles animate.
    private HashMap<N, Particle> nodes;              // The graph nodes to be drawn.
    private HashMap<E, Spring> edges;              // The graph edges to be drawn.
    private HashMap<N, Particle> stakes;       // Fixed particles for tethering a node to its location.
    private HashMap<Particle, Spring> tethers; // Tethers between a node and its location.
    private ZoomPan zoomer;                      // For interactive zooming and panning.
    private N selectedNode;                           // Optionally selected node for query or interaction.
    
                                /** Default strength for all edges. */
    public static final float EDGE_STRENGTH   = 1;
                                /** Default strength for all springs. */
    public static final float SPRING_STRENGTH = 0.5f;
                                /** Default damping for all particle movements. */
    public static final float DAMPING         = 0.1f;

    // ------------------------------- Constructors --------------------------------

    /** Initialises the particle viewer.
     *  @param parent Parent sketch in which this viewer is to be drawn.
     *  @param width Width of the particle viewer in pixel units.
     *  @param height Height of the particle viewer in pixel units.
     */
    public MyParticleViewer(PApplet parent, int width, int height)
    {
        this.parent = parent;
        zoomer = new ZoomPan(parent);
        zoomer.setMouseMask(PConstants.SHIFT);
        centroid = new Smoother3D(0.9f); 
        physics  = new ParticleSystem(0, 0.75f);    // No gravity with .75 drag.
        nodes = new HashMap<N, Particle>();
        edges = new HashMap<E, Spring>();
        stakes = new HashMap<N,Particle>();
        tethers = new HashMap<Particle,Spring>();
        this.width = width;
        this.height = height;
        isPaused = false;
        selectedNode = null;
    }

    // ---------------------------------- Methods ----------------------------------

    /** Updates the particle view. This should be called on each draw cycle in order
     *  to update the positions of all nodes and edges in the viewer. If you need to update
     *  the positions of particles without drawing it (e.g. to speed up movement, call 
     *  updateParticles() instead.
     */
    public void draw()
    {
        parent.pushStyle();
        parent.pushMatrix();
        zoomer.transform();
        updateCentroid();
        centroid.tick();

        parent.translate(width/2, height/2);
        parent.scale(centroid.getZ());
        parent.translate(-centroid.getX(), -centroid.getY());

        if (!isPaused)
        {
            updateParticles();
        }

        // Ensure that any selected element is positioned at the mouse location.
        if (selectedNode != null)
        {
            Particle p = nodes.get(selectedNode);
            p.makeFixed();
            float mX = (zoomer.getMouseCoord().x -(width/2))/centroid.getZ() + centroid.getX();
            float mY = (zoomer.getMouseCoord().y -(height/2))/centroid.getZ() + centroid.getY();
            p.position().set(mX,mY,0); 
        }

        // Draw edges if we have positive stroke weight.
        if (parent.g.strokeWeight > 0)
        {
            parent.stroke(0,180);
            parent.noFill();

            for (Map.Entry<E,Spring> row: edges.entrySet() )
            {
                Spring spring = row.getValue();
                Vector3D p1 = spring.getOneEnd().position();
                Vector3D p2 = spring.getTheOtherEnd().position();
                row.getKey().draw(parent, p1.x(),p1.y(),p2.x(),p2.y());
            }
        }


        // Draw nodes.
        parent.noStroke();
        parent.fill(120,50,50,180);

        for (Map.Entry<N,Particle> row: nodes.entrySet() )
        {
            Vector3D pos = row.getValue().position();
            row.getKey().draw(parent, pos.x(),pos.y());
        }

        parent.popMatrix();
        parent.popStyle();
    }

    /** Updates the positions of nodes and edges in the viewer. This method does not normally need
     *  to be called as update happens every time draw() is called. Calling this method can be useful
     *  if you wish to speed up the movement of nodes and edges by updating their position more than
     *  once every draw cycle.
     */
    public void updateParticles()
    {
        physics.tick(0.3f);         // Advance time in the physics engine.
    }

    /** Sets the drag on all particles in the system. By default drag is set to 0.75 which 
     *  is enough to allow particles to move smoothly. 
     *  @param drag Drag effect (larger numbers slow down movement).
     */
    public void setDrag(float drag)
    {
        physics.setDrag(drag);
    }

    /** Creates a attractive or repulsive force between the two given nodes. If the two nodes
     *  already have a force between them, it will be replaced by this one.
     * @param node1 First of the two nodes to have a force between them. 
     * @param node2 Second of the two nodes to have a force between them.
     * @param force Force to create between the two nodes. If positive, the nodes will attract
     *              each other, if negative they will repulse. The larger the magnitude the stronger the force.
     * @return True if the viewer contains the two nodes and a force between them has been created.
     */
    public boolean addForce(N node1, N node2, float force)
    {
        return addForce(node1, node2, force, 0.1f);
    }

    /** Creates a attractive or repulsive force between the two given nodes. If the two nodes
     *  already have a force between them, it will be replaced by this one.
     * @param node1 First of the two nodes to have a force between them. 
     * @param node2 Second of the two nodes to have a force between them.
     * @param force Force to create between the two nodes. If positive, the nodes will attract
     *              each other, if negative they will repulse. The larger the magnitude the stronger the force.
     * @param minDistance Minimum distance within which no force is applied.
     * @return True if the viewer contains the two nodes and a force between them has been created.
     */
    public boolean addForce(N node1, N node2, float force, float minDistance)
    {
        Particle p1 = nodes.get(node1);
        if (p1 == null)
        {
            return false;
        }
        Particle p2 = nodes.get(node2);
        if (p2 == null)
        {
            return false;
        }

        // We may have to remove existing force if it exists between these two nodes.
        for (int i=0; i<physics.getNumAttractions(); i++)
        {
            Attraction a = physics.getAttraction(i);
            if (((a.getOneEnd() == p1) && (a.getTheOtherEnd() == p2)) ||
                ((a.getOneEnd() == p2) && (a.getTheOtherEnd() == p1)))
            {
                physics.removeAttraction(a);
                break;
            }
        }
        // Add the new force.
        physics.makeAttraction(p1,p2, force, minDistance);
        return false;
    }

    
    /** Creates a spring between the two given nodes. If the two nodes not directly connected by an
     *  edge already have a spring between them, it will be replaced by this one. The strength of the
     *  spring will be less than that of connected edges.
     * @param node1 First of the two nodes to have a spring between them. 
     * @param node2 Second of the two nodes to have a spring between them.
     * @param length The length of this spring (natural rest distance at which the two nodes would sit).
     * @return True if the viewer contains the two nodes and a spring between them has been created.
     */
    public boolean addSpring(N node1, N node2, float length)
    {
        return addSpring(node1,node2,length,SPRING_STRENGTH);
    }
    
    /** Creates a spring between the two given nodes with the given strength. If the two nodes not directly 
     *  connected by an edge already have a spring between them, it will be replaced by this one. 
     * @param node1 First of the two nodes to have a spring between them. 
     * @param node2 Second of the two nodes to have a spring between them.
     * @param length The length of this spring (natural rest distance at which the two nodes would sit).
     * @param strength The strength of this new spring. 
     * @return True if the viewer contains the two nodes and a spring between them has been created.
     */
    public boolean addSpring(N node1, N node2, float length, float strength)
    {
        Particle p1 = nodes.get(node1);
        if (p1 == null)
        {
            return false;
        }
        Particle p2 = nodes.get(node2);
        if (p2 == null)
        {
            return false;
        }

        // We may have to remove existing spring if it exists between these two nodes.
        for (int i=0; i<physics.getNumSprings(); i++)
        {
            Spring spring = physics.getSpring(i);
            if ((((spring.getOneEnd() == p1) && (spring.getTheOtherEnd() == p2)) ||
                ((spring.getOneEnd() == p2) && (spring.getTheOtherEnd() == p1))) &&
                (spring.strength() != EDGE_STRENGTH))
            {
                physics.removeSpring(spring);
                break;
            }
        }

        // Add the new force.
        physics.makeSpring(p1,p2, strength, DAMPING,length);
        return false;
    }
    
    /** Tethers the given node to its location with the given strength.
     *  @param node The node to be tethered.
     *  @param strength Strength of the tether.
     *  @return True if the viewer contains the given node and it was tethered successfully.
     */
    public boolean tether(N node, float strength)
    {
        Particle p1 = nodes.get(node);
        if (p1 == null)
        {
            return false;
        }
        
        // Grab the tethering stake if it has already been created, otherwise create a new one.
        Particle stake = stakes.get(node);
        if (stake == null)
        {
            stake = physics.makeParticle(1, node.getLocation().x, node.getLocation().y, 0);
            stake.makeFixed();
            stakes.put(node,stake);
        }
        
        // Grab the tether if it has already been created, otherwise create a new one.
        Spring tether = tethers.get(stake);
        if (tether == null)
        {
            tether = physics.makeSpring(stake, p1, strength, DAMPING, Float.MIN_VALUE);
            tethers.put(stake,tether);
        }
        else
        {
            tether.setStrength(strength);
        }
        return true;
    }
    
    /* * Sets the mass of the given node. The larger the mass, the stronger the attractive
     *  or repulsive force associated with this node.
     * @param node The node whose mass will be set.
     * @param mass Mass to be given to the node.
     * @return True if the viewer contains the given node and its mass was set successfully.
     * /
    public boolean setMass(N node, float mass)
    {
        Particle p = nodes.get(node);
        if (p== null)
        {
            return false;
        }
        p.setMass(mass);
        return true;
    }
     */

    public HashSet<N> getNodes() {
        return new HashSet<N>(nodes.keySet());
    }
    
    public HashSet<E> getEdges() {
        return new HashSet<E>(edges.keySet());
    }
    
    /** Provides the particle associated with the given node. This can be used for advanced 
     *  configuration of the node's behaviour in a force-directed layout.
     *  @param node The node for which the associated particle is to be retrieved. 
     *  @return The particle representing the given node or null if it is not found.
     */
    public Particle getParticle(N node)
    {
        return nodes.get(node);
    }
    
    /** Reports the currently selected node of null if no nodes selected. A selected node
     *  is one that has been clicked with the mouse and can be dragged once selected. This
     *  method can be useful when you wish to display some extra characteristics associated
     *  with a user-chosen node. Note that a node can only be selected while the mouse button 
     *  is down and isn't masked with a shift key used for zooming the display.
     *  @return The selected node or null if no node is currently selected.
     */
    public N getSelectedNode()
    {
        return selectedNode;
    }
    
    /** Reports the node nearest to the given screen coordinates.
     *  @param x x screen coordinate to query
     *  @param y y screen coordinate to query
     *  @return Node nearest to the given screen coordinates or null if no nodes in the particle viewer.
     */
    public N getNearest(float x, float y)
    {
        return getNearest(x,y,-1);
    }
    
    
    /** Reports the node nearest to the given screen coordinates but within the given radius.
     *  @param x x screen coordinate to query
     *  @param y y screen coordinate to query
     *  @param radius Radius within which to search for nodes. If negative, all nodes are searched.
     *  @return Node nearest to the given screen coordinates or null if no nodes found within the given radius of the coordinates.
     */
    public N getNearest(float x, float y, float radius)
    {
        float mX = (x - width/2)/centroid.getZ() + centroid.getX();
        float mY = (y - height/2)/centroid.getZ() + centroid.getY();

        float nearestDSq = radius*radius;
        N nearestNode = null;

        for (Map.Entry<N,Particle> row: nodes.entrySet())
        {
            N node = row.getKey();
            Particle p = row.getValue();

            float px = p.position().x();
            float py = p.position().y();
            float dSq = (px-mX)*(px-mX) + (py-mY)*(py-mY);
            if (dSq < nearestDSq)
            {
                nearestDSq = dSq;
                nearestNode = node;
            }
        }
        return nearestNode;
    }

    /** Adds a node to those to be displayed in the viewer.
     * @param node Node to add to the viewer.
     */
    public void addNode(N node)
    {
        Particle p = physics.makeParticle(1, node.getLocation().x, node.getLocation().y, 0);
        nodes.put(node,p);
    }
    
    public void removeNode(N node)
    {
        edges.keySet().removeAll(node.getInEdges());
        edges.keySet().removeAll(node.getOutEdges());
        nodes.remove(node);
    }
    
    public void removeEdge(E edge) {
        edges.keySet().remove(edge);
    }
    
    /** Adds the given edge to those to be displayed in the viewer. Note that the edge must connect
     *  nodes that have already been added to the viewer. This version will use the locations of the
     *  two nodes to calculate their distance of separation. 
     *  @param edge Edge to add to the display.
     *  @return True if edge was added successfully. False if edge contains nodes that have not been
     *               added to the viewer.
     */
    public boolean addEdge(E edge)
    {
        Particle p1 = nodes.get(edge.getNode1());
        if (p1 == null)
        {
            System.err.println("Warning: Node1 not found when creating edge.");
            return false;
        }
        Particle p2 = nodes.get(edge.getNode2());
        if (p2 == null)
        {
            System.err.println("Warning: Node2 not found when creating edge.");
            return false;
        }

        // Only add edge if it does not already exist in the collection
        if (!edges.containsKey(edge))
        {
            float x1 = p1.position().x();
            float y1 = p1.position().y();
            float x2 = p2.position().x();
            float y2 = p2.position().y();
            // Strength, damping, reset length
            edges.put(edge, physics.makeSpring(p1, p2, 
                      EDGE_STRENGTH, DAMPING, (float)Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2))));
        }
        return true;
    }
    
    /** Adds the given edge to those to be displayed in the viewer. Note that the edge must connect
     *  nodes that have already been added to the viewer. This version will fix the distance of 
     *  separation between nodes to the given value
     *  @param edge Edge to add to the display.
     *  @param distance Distance of separation between the nodes.
     *  @return True if edge was added successfully. False if edge contains nodes that have not been
     *               added to the viewer.
     */
    public boolean addEdge(E edge, float distance)
    {
        Particle p1 = nodes.get(edge.getNode1());
        if (p1 == null)
        {
            System.err.println("Warning: Node1 not found when creating edge.");
            return false;
        }
        Particle p2 = nodes.get(edge.getNode2());
        if (p2 == null)
        {
            System.err.println("Warning: Node2 not found when creating edge.");
            return false;
        }

        // Only add edge if it does not already exist in the collection
        if (!edges.containsKey(edge))
        {
            // Strength, damping, reset length
            edges.put(edge, physics.makeSpring(p1, p2, EDGE_STRENGTH, DAMPING, distance));
        }
        return true;
    }

    /** Attempts to space out non-connected nodes from one another. This is achieved by adding a strong repulsive force
     *  between non-connected nodes. Note that this produces n-squared forces so can be slow for large networks where
     *  many nodes are not connected to each other. 
     */
    public void spaceNodes()
    {
        ArrayList<Particle> pList = new ArrayList<Particle>(nodes.values());
        for (int i=0; i<pList.size(); i++)
        {
            for (int j=0; j<pList.size();j++)
            {
                if (i>j)
                {
                    Particle p1 = pList.get(i);
                    Particle p2 = pList.get(j);
                    
                    // See if we have a connection between nodes
                    for (Spring spring : edges.values())
                    {
                        if (((spring.getOneEnd() == p1) && (spring.getTheOtherEnd() == p2)) ||
                            ((spring.getOneEnd() ==p2) && (spring.getTheOtherEnd()== p1)))
                        {
                            // Do nothing as we already have an edge connecting these two particles
                        }
                        else
                        {
                            // Add a small repulsive force
                            physics.makeAttraction(p1,p2, -1000, 0.1f);
                        }
                    }
                }
            }
        }
    }

    /** Allows a node to be selected with the mouse.
     */
    public void selectNearestWithMouse()
    {
        if (!zoomer.isMouseCaptured())
        {
            float mX = (zoomer.getMouseCoord().x -(width/2))/centroid.getZ() + centroid.getX();
            float mY = (zoomer.getMouseCoord().y -(height/2))/centroid.getZ() + centroid.getY();

            if (selectedNode == null)
            {
                float nearestDSq = Float.MAX_VALUE;

                for (Map.Entry<N,Particle> row: nodes.entrySet())
                {
                    N node = row.getKey();
                    Particle p = row.getValue();

                    float px = p.position().x();
                    float py = p.position().y();
                    float dSq = (px-mX)*(px-mX) + (py-mY)*(py-mY);
                    if (dSq < nearestDSq)
                    {
                        nearestDSq = dSq;
                        selectedNode = node;
                    }
                }
            }
        }
    }
    
    public E getEdge(N node1, N node2) {
        for (E edge : edges.keySet()) {
            if ((edge.getNode1().equals(node1) && edge.getNode2().equals(node2))
                || (edge.getNode1().equals(node2) && edge.getNode2().equals(node1))) {
                    return edge;
            }
        }
        return null;
    }
    
    public PVector getMousePosition() {
        float mX = (zoomer.getMouseCoord().x -(width/2))/centroid.getZ() + centroid.getX();
        float mY = (zoomer.getMouseCoord().y -(height/2))/centroid.getZ() + centroid.getY();
        
        return new PVector(mX, mY);
    }

    /** Releases the mouse-selected node so that it readjusts in response to other node positions.
     */
    public void dropSelected()
    {
        if (!zoomer.isMouseCaptured())
        {            
            if (selectedNode != null)
            {
                nodes.get(selectedNode).makeFree();
                selectedNode = null;
            }
        }
    }

    /** Resets the zoomed view to show the entire network.
     */
    public void resetView()
    {
        zoomer.reset();
    }

    // ------------------------------ Private methods ------------------------------

    /** Centres the particle view on the currently visible nodes.
     */
    private void updateCentroid()
    {
        float xMax = Float.NEGATIVE_INFINITY, 
        xMin = Float.POSITIVE_INFINITY, 
        yMin = Float.POSITIVE_INFINITY, 
        yMax = Float.NEGATIVE_INFINITY;

        for (int i=0; i<physics.getNumParticles(); ++i)
        {
            Particle p = physics.getParticle(i);
            xMax = Math.max(xMax, p.position().x());
            xMin = Math.min(xMin, p.position().x());
            yMin = Math.min(yMin, p.position().y());
            yMax = Math.max(yMax, p.position().y());
        }

        float xRange = xMax-xMin;
        float yRange = yMax-yMin;
        
        if ((xRange==0) && (yRange ==0))
        {
            xRange = Math.max(1, xMax);
            yRange = Math.max(1, yMax);
        }
        float zScale = (float)Math.min(height/(yRange*1.2f),width/(xRange*1.2f));
        centroid.setTarget(xMin+0.5f*xRange, yMin+0.5f*yRange, zScale);
    }
}
    public void settings() {  size(1920, 1080); }
    static public void main(String[] passedArgs) {
        String[] appletArgs = new String[] { "coda_2" };
        if (passedArgs != null) {
          PApplet.main(concat(appletArgs, passedArgs));
        } else {
          PApplet.main(appletArgs);
        }
    }
}
