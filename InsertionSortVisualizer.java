/*
 * InsertionSortVisualizer.java
 * Quinton Bock
 * 3/21/2025
 */

 import java.awt.*;
 import java.awt.event.ActionListener;
 import java.util.Random;
 import javax.swing.*;
 
 /*
  * InsertionSortVisualizer is a JPanel-based class that visually demonstrates
  * the insertion sort algorithm. It includes controls for starting, pausing, resetting,
  * and adjusting sorting parameters.
  */
 public class InsertionSortVisualizer extends JPanel {
     
     // Arrays for current and original (unsorted) data.
     private int[] array, originalArray;
     
     /* 
      * Indices to highlight the:
          * keyIndex         -> current KEY element's initial index
          * currentIndex     -> value of key value's CURRENT index
          * comparisonIndex  -> next element up for COMPARISON to the key value
          * sortedIndex      -> boundary between SORTED and unsorted portions
      */
     private int keyIndex = -1, currentIndex = -1, comparisonIndex = -1, sortedIndex = -1;
     
     /* 
      * Flags to track whether sorting is in progress or paused.
          * WAITING  -> NOT sorting && NOT paused
          * RUNNING  ->     sorting && NOT paused
          * FINISHED -> NOT sorting &&     paused
          * PAUSED   ->     sorting &&     paused
      */
     private boolean isSorting = false, isPaused = false;
 
     // An object used for thread synchronization during pause/resume.
     private final Object pauseLock = new Object();
     
     /* 
      * GUI components for controlling the visualization:
          * startButton          -> start/pause/resume the sorting process
          * resetButton          -> reset/refresh the array and sorting process
          * numValuesField       -> number of values in the array
          * valueRangeField      -> range for each value in the array
          * sortingSpeedSlider   -> speed control for the sorting animation
      */
     private JButton startButton, resetButton;
     private JTextField numValuesField, valueRangeField;
     private JSlider sortingSpeedSlider;
     private JLabel numValuesLabel, valueRangeLabel, sortingSpeedLabel;
 
     // Constants for the maximum number of values and range.
     private static final int MAX_NUM_VALUES = 200;  // Anything larger -> bars are too small to see.
     private static final int MAX_VALUE_RANGE = 99;  // Anything larger -> text overlaps with surrounding bars' text
 
     
     // Animation delay value for controlling sort speed.
     private double animationDelay;
     // Maximum delay constant (in milliseconds).
     private static final double MAX_DELAY = 3000.0;
     
     /* 
      * Padding for GUI elements and visual representation.
          * COMPARISON_PADDING -> space between current comparison element(s) and the rest of the sorted array (current comparison isolated)
          * WALL_PADDING       -> space between the walls of the window and the closest element (leftmost bar, rightmost bar, and top of bars)
          * SORTED_PADDING     -> space between all sorted/sorting elements and the rest of the unsorted elements
      */
     private static final int COMPARISON_PADDING = 300, WALL_PADDING = 100, SORTED_PADDING = 100;
     
     /* 
      * Default parameters for the visualization.
          * NUM_VALUES    -> default number of values in the array 
          * VALUE_RANGE   -> default range for each value in the array
          * SORTING_SPEED -> default sorting speed (exponential from slow sorting to infinitely fast sorting)
      */
     private static final int NUM_VALUES = 100, VALUE_RANGE = 50, SORTING_SPEED = 80;
     
     // Variables to keep track of the last used number of values and range.
     private int lastNumValues = NUM_VALUES, lastValueRange = VALUE_RANGE;
     
     // Custom color for sorted elements.
     private static final Color DARK_GREEN = new Color(0, 100, 0);
     
     // Thread that performs the sorting visualization.
     private Thread sortingThread;
 
     /*
      * Constructor: Initializes the array with random values, sets the animation delay
      * based on the default sorting speed, clones the original array, and paints the initial array.
      */
     public InsertionSortVisualizer() {
         // Allocate the array with the default number of values.
         this.array = new int[NUM_VALUES];
 
         // Calculate the animation delay using a cubic relationship to the sorting speed.
         this.animationDelay = MAX_DELAY * Math.pow(1 - (double) (SORTING_SPEED / 100.0), 3);
 
         // Fill the array with random numbers within the specified value range.
         Random rand = new Random();
         for (int i = 0; i < NUM_VALUES; i++) {
             this.array[i] = rand.nextInt(VALUE_RANGE) + 1;
         }
 
         // Clone the initial array to preserve the original unsorted values.
         this.originalArray = array.clone();
 
         // Trigger a repaint to display the initial unsorted array.
         this.repaint();
     }
     
     /*
      * updateArray: Updates the array with a new number of values, value range, and sorting speed.
      * It then repaints the window to reflect the new configuration.
      */
     public void updateArray(int numValues, int valueRange, int sortingSpeed) {
         // Create a new array with the given number of values.
         this.array = new int[numValues];
 
         // Update the animation delay based on the new sorting speed.
         setSortingSpeed(sortingSpeed);
 
         // Populate the array with random values in the new range.
         Random rand = new Random();
         for (int i = 0; i < numValues; i++) {
             this.array[i] = rand.nextInt(valueRange) + 1;
         }
 
         // Save a copy of the new array as the original unsorted array.
         this.originalArray = array.clone();
 
         // Refresh the window display.
         this.repaint();
     }
 
     /*
      * setSortingSpeed: Adjusts the animation delay based on the sorting speed.
      * The animation delay is the amount of time that the program waits between each step of the sorting process so the user can see the changes.
      * The delay is calculated such that the user can find a speed that works for them. The effect is as follows:
      * At maximum speed (100) -> delay is at minimum (0)    -> sort runs instantly.
      * At minimum speed (0)   -> delay is at maximum (3000) -> sort runs very slowly for time to understand each step
      */
     public void setSortingSpeed(int sortingSpeed) {
         if (sortingSpeed == 100)
             this.animationDelay = 0;
         else 
             // Delay is calculated using a squared relationship to the speed (for a non-linear effect).
             this.animationDelay = MAX_DELAY * Math.pow(1 - (double) (sortingSpeed / 100.0), 2);
     }
     
     /*
      * reset: Resets the array back to its original state (before the sort started) and resets all indices and flags.
      * Finally, repaints the window to reflect the reset.
      */
     public void reset() {
         // Copy the original array back into the working array.
         System.arraycopy(originalArray, 0, array, 0, originalArray.length);
 
         // Mark the sort as WAITING (not sorting and not paused).
         isSorting = false;
         isPaused = false;
 
         // Reset all highlighting indices.
         keyIndex = -1;
         currentIndex = -1;
         comparisonIndex = -1;
         sortedIndex = -1;
 
         // Update the visual display.
         this.repaint();
     }
 
     /*
      * insertionSort: Initiates the insertion sort algorithm on the array and visually updates
      * the panel at each step. Uses a separate thread to run the sorting animation.
      */
     public void insertionSort() {
         if (isSorting) return; // Prevent starting if a sort is already in progress.
         isSorting = true;
 
         sortingThread = new Thread(() -> {
             try {
                 // Iterate through the array for insertion sort.
                 for (int i = 0; i < array.length; i++) {
                     if (!isSorting) return; // Check for reset request
                     
                     // After the first iteration, once the sorted array has values, take a break to show the user the current sorted array without making any comparisons
                     if (i > 0) {
                         currentIndex = -1;
                         comparisonIndex = -1;
                         sortedIndex = i;
                         repaint();
                         delay();
                     }
                     
                     if (!isSorting) return; // Check for reset request
                     
                     // Mark the current element as the key for insertion.
                     keyIndex = i;        // Original index of the key
                     int key = array[i];  // Value of the key
                     int j = i - 1;       // First element to be compared with key
                     currentIndex = i;    // Current index of the key
                     comparisonIndex = j; // Value of the first comparison element
                     sortedIndex = j;     // last element in the sorted array
                     repaint();
                     delay();
 
                     // If there is a value to the left of the key that is greater than the key, shift the key to the left
                     // Otherwise, the key and everything to the left is already sorted
                     while (j >= 0 && array[j] > key) {
                         array[j + 1] = array[j];
                         array[j] = key;
                         currentIndex = j;
                         j = j - 1;
                         comparisonIndex = j;
                         sortedIndex = j;
                         repaint();
                         delay();
                     }
                     
                     // Synchronize on pauseLock to check if a pause has been requested.
                     synchronized (pauseLock) {
                         while (isPaused) {
                             if (!isSorting) return; // Check for reset request
                             try {
                                 // Wait until notified to resume.
                                 pauseLock.wait();
                             } catch (InterruptedException e) {
                                 return;
                             }
                         }
                     }
                 }
 
                 if (!isSorting) return; // Check for reset request
                 
                 // Once sorting is complete, update indices and flags to reflect the FINISHED state.
                 currentIndex = -1;          // No more unsorted elements
                 comparisonIndex = -1;       // No more comparisons to make
                 sortedIndex = array.length; // Entire array is sorted
                 isSorting = false;          // Sorting is complete
                 isPaused = true;            // Sorting is paused (since it's complete)
 
                 // Update the buttons to show the appropriate labels for the FINISHED state.
                 startButton.setVisible(false);
                 resetButton.setText("Reset");
                 resetButton.setVisible(true);
                 repaint();
             } catch (Exception e) {
                 // Print an error if the sorting thread is interrupted.
                 System.err.println("Sorting thread was interrupted: " + e.getMessage());
             }
         });
         // Start the sorting thread.
         sortingThread.start();
     }
     
     /*
      * delay: Handles the delay between sorting steps. It checks periodically if the
      * thread should pause or if the sorting process has been cancelled.
      */
     @SuppressWarnings("BusyWait")
     private void delay() {
         if (!isSorting) return; // Check for reset request
         long remainingDelayTime = (long) animationDelay;
         long checkInterval = 5; // Check every 5 milliseconds.
         while (remainingDelayTime > 0) {
             synchronized (pauseLock) {
                 while (isPaused) {
                     try {
                         // Wait while the sorting is paused.
                         pauseLock.wait();
                     } catch (InterruptedException e) {
                         sortingThread.interrupt();
                         return;
                     }
                 }
             }
             // Delay for a small interval to allow responsiveness.
             long delayTime = Math.min(checkInterval, remainingDelayTime);
             try {
                 Thread.sleep(delayTime);
             } catch (InterruptedException e) {
                 System.err.println("Thread interrupted: " + e.getMessage());
                 return;
             }
             remainingDelayTime -= delayTime;
         }
     }
 
     /*
      * paintComponent: Custom painting method that draws the array as a series of bars.
      * The color and position of each bar changes depending on its state in the sorting process.
      */
     @Override
     protected void paintComponent(Graphics g) {
         super.paintComponent(g);
         int totalBars = array.length; // Total number of bars corresponds to the length of the array.
         int totalWidth = 1600; // Relative width of the window.
         int controlPanelHeight = 20; // Space (height) allocated for control panel at the bottom.
         int availableHeight = getHeight() - WALL_PADDING - controlPanelHeight; // Total height available for drawing bars.
 
         /* 
          * Calculate available space for bars with no padding, based on the available width and number of bars. 
          * Allocated space: 
              * between bars in each section
              * between each wall and it's closest bar
              * between the sorted array and the unsorted array
              * between the current comparison and the surrounding array, to isolate the comparison for viewing
          */
         int wholeBar = (totalWidth - (2 * WALL_PADDING) - SORTED_PADDING - COMPARISON_PADDING) / totalBars;
 
         // Bar width is 3/4 of space available for each bar, leaving a 1/4 of the space for padding between bars.
         int barWidth = wholeBar - wholeBar / 4;
 
         // Set font size based on bar width for drawing numbers.
         int fontSize = Math.max(8, barWidth / 2);
         g.setFont(new Font("Arial", Font.BOLD, fontSize));
         FontMetrics fm = g.getFontMetrics();
         
         // Finds the largest value in the array and ensures that the graph fills the available height,
         // even if the largest value in the random array is much smaller than the theoretical maximum.
         int maxValue = 0;
         for (int value : array) {
             if (value > maxValue) {
                 maxValue = value;
             }
         }
         double scalingFactor = (double) availableHeight / maxValue;
         
         // Loop through the array and draw each bar.
         for (int i = 0; i < array.length; i++) {
             int x = WALL_PADDING + i * wholeBar;
             int barHeight = (int) (array[i] * scalingFactor);
             int y = getHeight() - barHeight - controlPanelHeight;
 
             /*
              * Set color and adjust x-position based on the state of the element.
                 * DARK_GREEN -> SORTED elements (ALREADY been a key) 
                 *               Not compared to the current key yet
                 *               Could be greater than current key but not sure yet
 
                 *        RED -> Current element being compared to the current key element
 
                 *       BLUE -> Current key element
 
                 *      GREEN -> SORTED elements (ALREADY been a key)
                 *               Already Compared to the current key
                 *               Already declared greater than current key
 
                 *      BLACK -> UNSORTED elements (Has NOT BEEN a key yet)
              */
             if (i < sortedIndex) {
                 g.setColor(DARK_GREEN);
             } else if (i == comparisonIndex) {
                 x += COMPARISON_PADDING / 2;
                 g.setColor(Color.RED);
             } else if (i == currentIndex) {
                 x += COMPARISON_PADDING / 2;
                 g.setColor(Color.BLUE);
             } else if (i <= keyIndex) {
                 x += COMPARISON_PADDING / 2;
                 x += SORTED_PADDING;
                 g.setColor(Color.GREEN);
             } else {
                 x += COMPARISON_PADDING;
                 x += SORTED_PADDING;
                 g.setColor(Color.BLACK);
             }
 
             // Draw the filled rectangle representing the array element.
             g.fillRect(x, y, barWidth, barHeight);
 
             // Draw the numeric value on top of the bar.
             g.setColor(Color.BLACK);
             String numText = String.valueOf(array[i]);
             int textWidth = fm.stringWidth(numText);
             int textX = x + (barWidth - textWidth) / 2;
             int textY = y - 5;
             g.drawString(numText, textX, textY);
         }
     }
 
     /*
      * main: The entry point of the program. Sets up the JFrame, initializes the visualizer,
      * creates the control panel with buttons and sliders, and assigns action listeners to handle
      * user interactions.
      */
     public static void main(String[] args) {
         // Create the main application window.
         JFrame frame = new JFrame("Insertion Sort Visualizer");
 
         // Instantiate the visualizer.
         InsertionSortVisualizer visualizer = new InsertionSortVisualizer();
         
         // Initialize control components.
         visualizer.startButton = new JButton("Start");
         visualizer.resetButton = new JButton("Refresh");
         visualizer.numValuesField = new JTextField(String.valueOf(NUM_VALUES), 3);
         visualizer.valueRangeField = new JTextField(String.valueOf(VALUE_RANGE), 2);
         visualizer.sortingSpeedSlider = new JSlider(0, 100, SORTING_SPEED);
         visualizer.numValuesLabel = new JLabel("Number of Values:");
         visualizer.valueRangeLabel = new JLabel("Value Range:");
         visualizer.sortingSpeedLabel = new JLabel("Sorting Speed:");
         Dimension buttonSize = new Dimension(100, 25);
         visualizer.startButton.setPreferredSize(buttonSize);
         visualizer.resetButton.setPreferredSize(buttonSize);
         visualizer.numValuesField.setPreferredSize(buttonSize);
         visualizer.valueRangeField.setPreferredSize(buttonSize);
         
         /*
          * Define the action listener for button events.
          * The listener handles the behavior of the buttons based on the current state of the program.
          * The program can be in one of four states: WAITING, RUNNING, PAUSED, or FINISHED.
          * The behavior/visibility of the buttons/input fields change based on the current state:
              * WAITING  -> The program is waiting for the user to start the sorting process.
              *                  - START the sorting process, in turn refreshing the array with the new parameters.
              *                  - REFRESH the array with the new parameters before starting the sorting process.
              *                  - change the number of values, value range, and sorting speed.
              * RUNNING  -> The program is currently sorting the array.
              *                  - PAUSE the sorting process.
              * PAUSED   -> The program is paused during the sorting process.
              *                  - RESUME the sorting process.
              *                  - RESET the sorting process and restore the original settings.
              *                  - change the sorting speed.
              * FINISHED -> The program has finished sorting the array.
              *                  - RESET the sorting process and restore the original settings.
          */
         ActionListener buttonListener = e -> {
             JButton clickedButton = (JButton) e.getSource();
             
             // Handle Start button clicks.
             if (clickedButton == visualizer.startButton) {
                 if (visualizer.isSorting) {
                     if (visualizer.isPaused) {
                         // If the program is paused, resume sorting. (Transition from PAUSED -> RUNNING)
                         new Thread(() -> {
                             synchronized (visualizer.pauseLock) {
                                 visualizer.isPaused = false;
                                 visualizer.startButton.setText("Pause");
                                 visualizer.resetButton.setVisible(false);
                                 visualizer.sortingSpeedSlider.setVisible(false);
                                 visualizer.setSortingSpeed(visualizer.sortingSpeedSlider.getValue());
                                 visualizer.pauseLock.notifyAll();
                             }
                         }).start();
                     } else {
                         // If the program is running, pause the sorting process. (Transition from RUNNING -> PAUSED)
                         new Thread(() -> {
                             synchronized (visualizer.pauseLock) {
                                 visualizer.isPaused = true;
                                 visualizer.startButton.setText("Resume");
                                 visualizer.resetButton.setText("Reset");
                                 visualizer.resetButton.setVisible(true);
                                 visualizer.sortingSpeedSlider.setVisible(true);
                                 visualizer.pauseLock.notifyAll();
                             }
                         }).start();
                     }
                 } else {
                     // If the program hasn't started yet, initialize the sort with provided parameters. (Transition from WAITING -> RUNNING)
                     try {
                         int numValues = Integer.parseInt(visualizer.numValuesField.getText());
                         if(numValues > MAX_NUM_VALUES)
                             numValues = MAX_NUM_VALUES; // Limit the maximum number of values.
                         int valueRange = Integer.parseInt(visualizer.valueRangeField.getText());
                         if(valueRange > MAX_VALUE_RANGE)
                             valueRange = MAX_VALUE_RANGE; // Limit the maximum range.
                         int sortingSpeed = visualizer.sortingSpeedSlider.getValue();
                         // If the number of values or range has changed, update the array.
                         if(numValues != visualizer.lastNumValues || valueRange != visualizer.lastValueRange) {
                             visualizer.lastNumValues = numValues;
                             visualizer.lastValueRange = valueRange;
                             visualizer.updateArray(numValues, valueRange, sortingSpeed);
                         }
                         else {
                             // Otherwise, just update the sorting speed.
                             visualizer.setSortingSpeed(sortingSpeed);
                         }
                     } catch (NumberFormatException ex) {
                         // Show error message if the user input is invalid.
                         JOptionPane.showMessageDialog(frame, "Please enter valid numbers for the values and range.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
                     }
                     // Start the sorting in a new thread.
                     new Thread(() -> {
                         visualizer.startButton.setText("Pause");
                         visualizer.resetButton.setVisible(false);
                         visualizer.numValuesField.setVisible(false);
                         visualizer.valueRangeField.setVisible(false);
                         visualizer.sortingSpeedSlider.setVisible(false);
                         visualizer.numValuesLabel.setVisible(false);
                         visualizer.valueRangeLabel.setVisible(false);
                         visualizer.sortingSpeedLabel.setVisible(false);
                         visualizer.insertionSort();
                     }).start();
                 }
             } 
             // Handle Reset button clicks.
             else if (clickedButton == visualizer.resetButton) {
                 if (!visualizer.isPaused && !visualizer.isSorting) {
                     // If sorting hasn't started, refresh the array with new parameters. (Stay in WAITING state but update parameters)
                     try {
                         int numValues = Integer.parseInt(visualizer.numValuesField.getText());
                         if(numValues > MAX_NUM_VALUES) 
                             numValues = MAX_NUM_VALUES;   // Limit the maximum number of values.
                         int valueRange = Integer.parseInt(visualizer.valueRangeField.getText());
                         if(valueRange > MAX_VALUE_RANGE)
                             valueRange = MAX_VALUE_RANGE; // Limit the maximum range.
                         int sortingSpeed = visualizer.sortingSpeedSlider.getValue();
                         visualizer.lastNumValues = numValues;
                         visualizer.lastValueRange = valueRange;
                         visualizer.updateArray(numValues, valueRange, sortingSpeed);
                     } catch (NumberFormatException ex) {
                         JOptionPane.showMessageDialog(frame, "Please enter valid numbers for the values and range.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
                     }
                 } else {
                     // If sorting is paused or finished, reset the program and restore original settings. (Transition from PAUSED/FINISHED -> WAITING)
                     synchronized (visualizer.pauseLock) {
                         if (visualizer.sortingThread != null && visualizer.sortingThread.isAlive()) {
                             visualizer.isSorting = false;
                             visualizer.isPaused = false;
                             visualizer.pauseLock.notifyAll();
                             visualizer.sortingThread.interrupt();
                         }
                     }
                     if (visualizer.sortingThread != null && visualizer.sortingThread.isAlive()) {
                         try {
                             visualizer.sortingThread.join();
                         } catch (InterruptedException ex) {
                             Thread.currentThread().interrupt();
                         }
                     }
                     visualizer.isSorting = false;
                     visualizer.isPaused = false;
                     visualizer.startButton.setText("Start");
                     visualizer.startButton.setVisible(true);
                     visualizer.resetButton.setText("Refresh");
                     visualizer.numValuesField.setVisible(true);
                     visualizer.valueRangeField.setVisible(true);
                     visualizer.sortingSpeedSlider.setVisible(true);
                     visualizer.numValuesLabel.setVisible(true);
                     visualizer.valueRangeLabel.setVisible(true);
                     visualizer.sortingSpeedLabel.setVisible(true);
                     visualizer.reset();
                     visualizer.repaint();
                 }
             }
         };
         // Attach the action listener to the buttons.
         visualizer.startButton.addActionListener(buttonListener);
         visualizer.resetButton.addActionListener(buttonListener);
         
         // Create the left panel for start/reset buttons and the sorting speed slider.
         JPanel leftPanel = new JPanel();
         leftPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
         leftPanel.add(Box.createRigidArea(new Dimension(20, 0)));
         leftPanel.add(visualizer.startButton);
         leftPanel.add(visualizer.resetButton);
         leftPanel.add(Box.createRigidArea(new Dimension(20, 0)));
         leftPanel.add(visualizer.sortingSpeedLabel);
         leftPanel.add(visualizer.sortingSpeedSlider);
         
         // Create the right panel for number of values and value range inputs.
         JPanel rightPanel = new JPanel();
         rightPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 0));
         rightPanel.add(visualizer.numValuesLabel);
         rightPanel.add(visualizer.numValuesField);
         rightPanel.add(visualizer.valueRangeLabel);
         rightPanel.add(visualizer.valueRangeField);
         rightPanel.add(Box.createRigidArea(new Dimension(20, 0)));
         
         // Combine both panels into a control panel.
         JPanel controlPanel = new JPanel(new BorderLayout());
         controlPanel.setPreferredSize(new Dimension(1600, 40));
         controlPanel.add(leftPanel, BorderLayout.WEST);
         controlPanel.add(rightPanel, BorderLayout.EAST);
         
         // Set up the main frame layout.
         frame.setLayout(new BorderLayout());
         frame.add(controlPanel, BorderLayout.SOUTH);
         frame.add(visualizer, BorderLayout.CENTER);
         int windowWidth = 1600;
         int windowHeight = 800;
         frame.setSize(windowWidth, windowHeight);
         frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         frame.setLocation(50, 100);
         frame.setVisible(true);
     }
 }
 