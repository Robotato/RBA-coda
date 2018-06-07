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
        fill(lerpColor(#4d44d5,
                       #ed6d27,
                       identity));

        // draw the dang thing!
        ellipse(px, py, 25, 25);
        
        // draw threshold, neighbor information
        fill(color(255, 255, 255, 100));
        arc(px, py, 25, 25, 1.5 * PI, lerp(1.5*PI, 1.5*PI + 2*PI, getPropInfectedNeighbors()));
        stroke(#ff0050);
        strokeWeight(1);
        float radius = 25 / 2.;
        float thresholdAngle = lerp(1.5*PI, 1.5*PI + 2*PI, threshold);
        line(px, py, px + cos(thresholdAngle)*radius, py + sin(thresholdAngle)*radius);
        
        // infection status
        if (infected) {
            fill(#ff0050);
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
float threshold = 0.5;
float xenophilia = 0.5;
float stochasticity = 0.1;

enum Mode { AddNode, RemoveNode, Connect, Disconnect, Infect, Select, Run }
Mode mode;
int lastStepFrame = 0;
final int STEPINTERVAL = 100;

MyParticleViewer<Person, Edge> viewer;
static float selectionRadius = 100;

void setup() {
    size(1920, 1080);
    
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

void draw() {
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

void generateConnections() {
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

void runStep() {
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

void keyPressed() {
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

void mousePressed() {
    //GUI Immunity
    if (frameCount - lastGuiInteraction < GUI_IMMUNITY) { return; }
    
    Person nearest = viewer.getNearest(mouseX, mouseY, selectionRadius);
    
    switch(mode) {
        case AddNode:
            PVector mousePos = viewer.getMousePosition();
            nextIdentity = min(1, max(0, nextIdentity + random(-.5*stochasticity, .5*stochasticity)));
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

void mouseReleased(){
    viewer.dropSelected();
}

public void clearbtn(int value) {
    viewer = new MyParticleViewer<Person, Edge>(this, width, height);
    viewer.addNode(new Person(100, 100, nextIdentity));
}

public void nextIdentitySlider(float value) {
    nextIdentity = value / 100.;
}

public void thresholdSlider(float value) {
    threshold = value / 100.;
}

public void xenophiliaSlider(float value) {
    xenophilia = value / 100.;
}

public void stochasticitySlider(float value) {
    stochasticity = value / 100.;
}