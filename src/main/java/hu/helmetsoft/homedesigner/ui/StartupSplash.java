package hu.helmetsoft.homedesigner.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/** Original architectural artwork and a five-second startup introduction. */
public final class StartupSplash {
    public static final Duration DURATION=Duration.seconds(5);
    private StartupSplash() {}

    public static Stage show(Runnable ready) {
        Stage stage=new Stage(StageStyle.UNDECORATED);
        stage.setTitle("Home Designer");
        Canvas art=new Canvas(760,440);
        draw(art.getGraphicsContext2D());
        Label title=new Label("Home Designer");
        title.setFont(Font.font("Segoe UI",FontWeight.LIGHT,46));
        title.setTextFill(Color.WHITE);
        Label credit=new Label("created by helmetsoft.com");
        credit.setFont(Font.font("Segoe UI",18));
        credit.setTextFill(Color.web("#A9D9DC"));
        ProgressBar progress=new ProgressBar(0);
        progress.setPrefWidth(210);
        progress.setMaxWidth(210);
        progress.setStyle("-fx-accent: #62D2BF; -fx-control-inner-background: #203A49;");
        VBox labels=new VBox(9,title,credit,progress);
        labels.setPadding(new Insets(0,0,32,40));
        labels.setAlignment(Pos.BOTTOM_LEFT);
        labels.setMouseTransparent(true);
        stage.setScene(new Scene(new StackPane(art,labels),760,440));
        Timeline timer=new Timeline(new KeyFrame(DURATION,new KeyValue(progress.progressProperty(),1)));
        timer.setOnFinished(e -> {
            stage.hide();
            ready.run();
        });
        stage.setOnShown(e -> timer.playFromStart());
        stage.setOnHidden(e -> timer.stop());
        stage.setOnCloseRequest(javafx.event.Event::consume);
        stage.show();
        stage.centerOnScreen();
        return stage;
    }

    private static void draw(GraphicsContext g) {
        g.setFill(new LinearGradient(0,0,1,1,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#112A3C")),new Stop(1,Color.web("#091722"))));
        g.fillRect(0,0,760,440);
        g.setFill(new LinearGradient(400,20,650,295,false,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#305C67",0.5)),new Stop(1,Color.TRANSPARENT)));
        g.fillOval(320,12,420,300);
        g.setFill(Color.web("#C3DFD8"));
        g.fillOval(653,49,28,28);
        g.setStroke(Color.web("#527B82",0.5)); g.setLineWidth(1);
        g.strokeLine(365,86,412,86);
        g.strokeLine(586,62,626,62);

        // Front elevation: roof peak above the facade, foundations on a horizontal ground line.
        g.setFill(Color.web("#1D3D40"));
        g.fillRoundRect(344,268,388,35,18,18);
        g.setStroke(Color.web("#558C80")); g.setLineWidth(1.5);
        g.strokeLine(339,274,734,274);
        g.setFill(Color.web("#A7ADA1"));
        g.fillRect(388,263,298,9);

        g.setFill(Color.web("#AD805B"));
        g.fillRect(548,173,132,90);
        g.setStroke(Color.web("#C79D74",0.6)); g.setLineWidth(1);
        for(int x=556;x<680;x+=9) g.strokeLine(x,178,x,263);
        g.setFill(Color.web("#263F48"));
        g.fillRect(541,162,148,12);
        g.setFill(Color.web("#A3C5BD"));
        g.fillRect(541,160,148,3);

        polygon(g,Color.web("#E8E4D7"),new double[]{392,470,548,548,392},
                new double[]{142,77,142,263,263});
        polygon(g,Color.web("#2B4651"),new double[]{378,470,562,552,470,388},
                new double[]{143,64,143,151,82,151});
        g.setStroke(Color.web("#80BBB1")); g.setLineWidth(2);
        g.strokeLine(378,143,470,64);
        g.strokeLine(470,64,562,143);

        g.setFill(Color.web("#304D56"));
        g.fillRect(410,153,119,75);
        g.setFill(new LinearGradient(0,153,0,228,false,CycleMethod.NO_CYCLE,
                new Stop(0,Color.web("#93BFC0")),new Stop(1,Color.web("#436C79"))));
        g.fillRect(415,158,109,65);
        g.setFill(Color.web("#EBD5AA",0.8));
        g.fillRect(419,162,29,57);
        g.setStroke(Color.web("#304D56")); g.setLineWidth(4);
        g.strokeLine(451,156,451,225);
        g.strokeLine(488,156,488,225);
        g.setStroke(Color.web("#D8E7DD",0.55)); g.setLineWidth(1.5);
        g.strokeLine(497,166,517,186);
        g.strokeLine(497,176,517,196);
        g.setFill(Color.web("#B7B6A5"));
        g.fillRect(406,228,127,5);

        g.setFill(Color.web("#304D56"));
        g.fillRect(452,108,36,27);
        g.setFill(Color.web("#9BC3C1"));
        g.fillRect(457,113,26,17);
        g.setStroke(Color.web("#304D56")); g.setLineWidth(2);
        g.strokeLine(470,111,470,133);

        g.setFill(Color.web("#243F49"));
        g.fillRect(565,190,45,73);
        g.setFill(Color.web("#456C73"));
        g.fillRect(570,195,35,68);
        g.setFill(Color.web("#E6CB9B"));
        g.fillRect(575,200,25,37);
        g.setStroke(Color.web("#E4C39A")); g.setLineWidth(2);
        g.strokeLine(598,245,598,252);
        g.setFill(Color.web("#243F49"));
        g.fillRect(624,190,39,46);
        g.setFill(Color.web("#9ABAB4"));
        g.fillRect(629,195,29,36);
        g.setStroke(Color.web("#46636A")); g.setLineWidth(2);
        g.strokeLine(644,195,644,231);
        g.setFill(Color.web("#F5DFA8"));
        g.fillRoundRect(554,193,4,12,2,2);

        // Garden, steps and path are below the upright windows and door.
        g.setFill(Color.web("#CBD0BE"));
        g.fillRect(560,263,55,6);
        g.setFill(Color.web("#91A99A"));
        g.fillRect(554,269,67,5);
        polygon(g,Color.web("#718C80"),new double[]{565,610,628,548},
                new double[]{276,276,300,300});
        g.setFill(Color.web("#3E7868"));
        g.fillRoundRect(389,251,54,16,12,12);
        g.fillOval(399,242,21,23);
        g.fillOval(418,246,18,18);
        g.fillRoundRect(627,251,57,16,12,12);
        g.fillOval(646,240,22,24);
        g.setFill(Color.web("#83AA86"));
        g.fillOval(405,246,13,12);
        g.fillOval(650,244,13,12);

        g.setStroke(Color.web("#A58B65")); g.setLineWidth(4);
        g.strokeLine(711,272,711,203);
        g.strokeLine(711,230,697,215);
        g.strokeLine(711,222,724,206);
        g.setFill(Color.web("#447E6D"));
        g.fillOval(686,167,46,54);
        g.fillOval(679,192,37,34);
        g.fillOval(710,183,30,39);
        g.setFill(Color.web("#6A9A7B"));
        g.fillOval(691,172,24,31);
        g.setStroke(Color.web("#7FA88C")); g.setLineWidth(1.5);
        g.strokeLine(354,273,351,260);
        g.strokeLine(354,273,360,263);
        g.strokeLine(727,274,730,263);
    }

    private static void polygon(GraphicsContext g,Color fill,double[] x,double[] y) {
        g.setFill(fill);
        g.fillPolygon(x,y,x.length);
    }
}
