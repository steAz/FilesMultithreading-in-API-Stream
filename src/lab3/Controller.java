/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lab3;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ForkJoinPool;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javax.imageio.ImageIO;

/**
 *
 * @author Kazanostra
 */
public class Controller extends Control implements Initializable {
    
    @FXML
    TableView<ImageProcessingJob> filesTable;

    @FXML
    TableColumn<ImageProcessingJob, String> imageNameColumn;
    @FXML
    TableColumn<ImageProcessingJob, Double> progressColumn;
    @FXML
    TableColumn<ImageProcessingJob, String> statusColumn;

    @FXML
    Label timeLabel;

    File outputDir = null;
    
    @FXML
    protected void chooseOutputDir(ActionEvent event) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        outputDir = dirChooser.showDialog(null);
    }
    
    @FXML 
    protected void addFileAction(ActionEvent event){
        if(outputDir == null)
            chooseOutputDir(null);
        else{
            FileChooser fileChooser = new FileChooser();
            FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("JPG images", "*.jpg");
            fileChooser.getExtensionFilters().add(filter);
            List<File> selectedFiles = fileChooser.showOpenMultipleDialog(null);
            
            selectedFiles.stream().map((file) -> new ImageProcessingJob(file)).forEachOrdered((imgProcJob) -> {
                filesTable.getItems().add(imgProcJob);
            });
        }
    }
    
    @FXML
    void startAction(ActionEvent event) {
        new Thread(this::processSingleThreaded).start();
    }
    
    @FXML
    void startSynchronousAction(ActionEvent event) {
        ForkJoinPool pool = new ForkJoinPool(4);
        pool.submit(this::processMultiThreaded);
        pool.shutdown();
    }
    
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        imageNameColumn.setCellValueFactory(
                p -> new SimpleStringProperty(p.getValue().getFile().getName()));
        statusColumn.setCellValueFactory(
                p -> p.getValue().getStatusProperty());
        progressColumn.setCellValueFactory(
                p -> p.getValue().getProgressProperty().asObject());
        progressColumn.setCellFactory(
                ProgressBarTableCell.<ImageProcessingJob>forTableColumn());
    }
    
    private void convertToGrayscale(
File originalFile, //oryginalny plik graficzny
    File outputDir, //katalog docelowy
    DoubleProperty progressProp//własność określająca postęp operacji
    ) {
        try {
            //wczytanie oryginalnego pliku do pamięci
            BufferedImage original = ImageIO.read(originalFile);
            //przygotowanie bufora na grafikę w skali szarości
            BufferedImage grayscale = new BufferedImage(
            original.getWidth(), original.getHeight(), original.getType());
            //przetwarzanie piksel po pikselu
            for (int i = 0; i < original.getWidth(); i++) {
            for (int j = 0; j < original.getHeight(); j++) {
            //pobranie składowych RGB
            int red = new Color(original.getRGB(i, j)).getRed();
            int green = new Color(original.getRGB(i, j)).getGreen();
            int blue = new Color(original.getRGB(i, j)).getBlue();
            //obliczenie jasności piksela dla obrazu w skali szarości
            int luminosity = (int) (0.21*red + 0.71*green + 0.07*blue);
            //przygotowanie wartości koloru w oparciu o obliczoną jaskość
            int newPixel =
            new Color(luminosity, luminosity, luminosity).getRGB();
            //zapisanie nowego piksela w buforze
            grayscale.setRGB(i, j, newPixel);
            }
            //obliczenie postępu przetwarzania jako liczby z przedziału [0, 1]
            double progress = (1.0 + i) / original.getWidth();
            //aktualizacja własności zbindowanej z paskiem postępu w tabeli
            Platform.runLater(() -> progressProp.set(progress));
            }
            //przygotowanie ścieżki wskazującej na plik wynikowy
            Path outputPath =
            Paths.get(outputDir.getAbsolutePath(), originalFile.getName());
            //zapisanie zawartości bufora do pliku na dysku
            ImageIO.write(grayscale, "jpg", outputPath.toFile());
        } catch (IOException ex) {
            //translacja wyjątku
            throw new RuntimeException(ex);
        }
    }
    
    private void processSingleThreaded(){
        long start = System.currentTimeMillis();
        
        filesTable.getItems().stream().forEach(this::handleJob);
        
        long end = System.currentTimeMillis();
        Platform.runLater(() -> timeLabel.setText("Elapsed time: " + Long.toString(end - start) + "ms"));
    }
    
    private void processMultiThreaded() {
        long start = System.currentTimeMillis();
        
        filesTable.getItems().parallelStream().forEach(this::handleJob);
        
        long end = System.currentTimeMillis();
        Platform.runLater(() -> timeLabel.setText("Elapsed time: " + Long.toString(end - start) + "ms"));
    }
    
    private void handleJob(ImageProcessingJob imgProcJob){
        if(imgProcJob.getState() == State.DONE){
            return;
        }
        
        imgProcJob.setStatusProperty("PROCESSING");
        convertToGrayscale(imgProcJob.getFile(), outputDir, imgProcJob.getProgressProperty());
        imgProcJob.setStatusProperty("DONE");
        
    }
    
    private enum State {

        WAITING, PROCESSING, DONE
    }

    private class ImageProcessingJob {
        private final File file;
        private final SimpleStringProperty statusProperty = new SimpleStringProperty();
        private final DoubleProperty progressProperty = new SimpleDoubleProperty(0);
        private State state; 
        
        
        public ImageProcessingJob(File file) {
            this.file = file;
            
            setStatusProperty("WAITING");
            progressProperty.setValue(0);
        }
        
        public State getState(){
            return state;
        }

        public void setStatusProperty(String state){
            statusProperty.setValue(state);
        }
        
        public File getFile() {
            return file;
        }

        public SimpleStringProperty getStatusProperty() {
            return statusProperty;
        }

        public DoubleProperty getProgressProperty() {
            return progressProperty;
        }
    }
}
