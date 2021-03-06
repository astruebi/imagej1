package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import ij.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.text.*;
import ij.gui.*;
import ij.util.*;
import ij.io.*;
import ij.process.*;
import ij.measure.*;

/** ImageJ plugin that does curve fitting using the modified CurveFitter class.
 *  Includes simplex settings dialog option.
 *
 * @author  Kieran Holland (email: holki659@student.otago.ac.nz)
 */
public class Fitter extends PlugInFrame implements PlugIn, ItemListener, ActionListener {

	Choice fit;
	Button doIt, open, apply;
	Checkbox settings;
	String fitTypeStr = CurveFitter.fitList[0];
	TextArea textArea;

	double[] dx = {0,1,2,3,4,5};
	double[] dy = {0,.9,4.5,8,18,24};
	double[] x,y;

	static CurveFitter cf;
	static int fitType = -1;
	static String equation = "y = a + b*x + c*x*x";
	static final int USER_DEFINED = -1;

	public Fitter() {
		super("Curve Fitter");
		WindowManager.addWindow(this);
		Panel panel = new Panel();
		fit = new Choice();
		for (int i=0; i<CurveFitter.fitList.length; i++)
			fit.addItem(CurveFitter.fitList[CurveFitter.sortedTypes[i]]);
		fit.addItem("*User-defined*");
		fit.addItemListener(this);
		panel.add(fit);
		doIt = new Button(" Fit ");
		doIt.addActionListener(this);
		panel.add(doIt);
		open = new Button("Open");
		open.addActionListener(this);
		panel.add(open);
		apply = new Button("Apply");
		apply.addActionListener(this);
		panel.add(apply);
		settings = new Checkbox("Show settings", false);
		panel.add(settings);
		add("North", panel);
		String text = "";
		for (int i=0; i<dx.length; i++)
			text += IJ.d2s(dx[i],2)+"  "+IJ.d2s(dy[i],2)+"\n";
		textArea = new TextArea("",15,30,TextArea.SCROLLBARS_VERTICAL_ONLY);
		//textArea.setBackground(Color.white);
		textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		if (IJ.isLinux()) textArea.setBackground(Color.white);
		textArea.append(text);
		add("Center", textArea);
		pack();
		GUI.center(this);
		show();
		IJ.register(Fitter.class);
	}

    /** Fit data in the textArea, show result in log and create plot.
     *  @param fitType as defined in CurveFitter constants
     *  @return false on error.
     */
	public boolean doFit(int fitType) {
		if (!getData()) {
            IJ.beep();
            IJ.showStatus("Data error: min. two (x,y) pairs needed");
			return false;
		}
		cf = new CurveFitter(x, y);
		try {
            if (fitType==USER_DEFINED) {
                String eqn = getEquation();
                if (eqn==null) return false;
                int params = cf.doCustomFit(eqn, null, settings.getState());
                if (params==0) {
                    IJ.log("Bad formula; should be:\n   y = function(x)");
                    return false;
                }
            } else
                cf.doFit(fitType, settings.getState());
            if (cf.getStatus() == Minimizer.INITIALIZATION_FAILURE) {
                IJ.beep();
                IJ.showStatus(cf.getStatusString());
                IJ.log("Curve Fitting Error:\n"+cf.getStatusString());
                return false;
            }
            if (Double.isNaN(cf.getSumResidualsSqr())) {
                IJ.beep();
                IJ.showStatus("Error: fit yields Not-a-Number");
                return false;
            }
            
		} catch (Exception e) {
            IJ.handleException(e);
            return false;
		}
        IJ.log(cf.getResultString());
		plot(cf);
		this.fitType = fitType; 
		return true;
	}
	
	String getEquation() {
		GenericDialog gd = new GenericDialog("Formula");
		gd.addStringField("Formula:", equation, 38);
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		equation = gd.getNextString();
		return equation;
	}
	
	public static void plot(CurveFitter cf) {
		double[] x = cf.getXPoints();
		double[] y = cf.getYPoints();
		if (cf.getParams().length<cf.getNumParams()) {
			Plot plot = new Plot(cf.getFormula(),"X","Y",x,y);
			plot.setColor(Color.BLUE);
			plot.addLabel(0.02, 0.1, cf.getName());
			plot.addLabel(0.02, 0.2, cf.getStatusString());
			plot.show();
			return;
		}
		double[] a = Tools.getMinMax(x);
		double xmin=a[0], xmax=a[1]; 
		a = Tools.getMinMax(y);
		double ymin=a[0], ymax=a[1]; 
		float[] px = new float[100];
		float[] py = new float[100];
		double inc = (xmax-xmin)/99.0;
		double tmp = xmin;
		for (int i=0; i<100; i++) {
			px[i]=(float)tmp;
			tmp += inc;
		}
		double[] params = cf.getParams();
		for (int i=0; i<100; i++)
			py[i] = (float)cf.f(params, px[i]);
		a = Tools.getMinMax(py);
		ymin = Math.min(ymin, a[0]);
		ymax = Math.max(ymax, a[1]);
		Plot plot = new Plot(cf.getFormula(),"X","Y",px,py);
		plot.setLimits(xmin, xmax, ymin, ymax);
		plot.setColor(Color.RED);
		plot.addPoints(x, y, PlotWindow.CIRCLE);
		plot.setColor(Color.BLUE);
		double yloc = 0.1;
		double yinc = 0.085;
		plot.addLabel(0.02, yloc, cf.getName()); yloc+=yinc;
		plot.addLabel(0.02, yloc, cf.getFormula());  yloc+=yinc;
        double[] p = cf.getParams();
        int n = cf.getNumParams();
        char pChar = 'a';
        for (int i = 0; i < n; i++) {
			plot.addLabel(0.02, yloc, pChar+" = "+IJ.d2s(p[i],5,9));
			yloc+=yinc;
			pChar++;
        }
		plot.addLabel(0.02, yloc, "R^2 = "+IJ.d2s(cf.getRSquared(),4));  yloc+=yinc;
		plot.setColor(Color.BLUE);
		plot.show();									
	}
	
	double sqr(double x) {return x*x;}
	
	boolean getData() {
		textArea.selectAll();
		String text = textArea.getText();
		text = zapGremlins(text);
		textArea.select(0,0);
		StringTokenizer st = new StringTokenizer(text, " \t\n\r,");
		int nTokens = st.countTokens();
		if (nTokens<4 || (nTokens%2)!=0)
			return false;
		int n = nTokens/2;
		x = new double[n];
		y = new double[n];
		for (int i=0; i<n; i++) {
			x[i] = getNum(st);
			y[i] = getNum(st);
		}
		return true;
	}
	
	void applyFunction() {
		if (cf==null || fitType <= 0) {
			IJ.error("No function available");
			return;
		}
		ImagePlus img = WindowManager.getCurrentImage();
		if (img==null) {
			IJ.noImage();
			return;
		}
		if (img.getTitle().startsWith("y=")) {
			IJ.error("First select the image to be transformed");
			return;
		}
		double[] p = cf.getParams();
		int width = img.getWidth();
		int height = img.getHeight();
		int size = width*height;
		float[] data = new float[size];
		ImageProcessor ip = img.getProcessor();
		float value;
		for (int y=0; y<height; y++) {
			for (int x=0; x<width; x++) {
				value = ip.getPixelValue(x,y);
				data[y*width+x] = (float)cf.f(p, value);
			}
		}
		ImageProcessor ip2 = new FloatProcessor(width, height, data, ip.getColorModel());
		new ImagePlus(img.getTitle()+"-transformed", ip2).show();
	}

	double getNum(StringTokenizer st) {
		Double d;
		String token = st.nextToken();
		try {d = new Double(token);}
		catch (NumberFormatException e){d = null;}
		if (d!=null)
			return(d.doubleValue());
		else
			return 0.0;
	}

	void open() {
		OpenDialog od = new OpenDialog("Open Text File...", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		String path = directory + name;
		textArea.selectAll();
		textArea.setText("");
		try {
			BufferedReader r = new BufferedReader(new FileReader(directory+name));
			while (true) {
				String s=r.readLine();
				if (s==null ||(s.length()>100))
					break;
				textArea.append(s+"\n");
			}
			r.close();
		}
		catch (Exception e) {
			IJ.error(e.getMessage());
			return;
		}
	}
	
	public void itemStateChanged(ItemEvent e) {
		fitTypeStr = fit.getSelectedItem();
	}
	
	public void actionPerformed(ActionEvent e) {
		try {
            if (e.getSource()==doIt)
                doFit(CurveFitter.getFitCode(fit.getSelectedItem()));
            else if (e.getSource()==apply)
                applyFunction();
            else
                open();
		} catch (Exception ex) {}
	}
	
	String zapGremlins(String text) {
		char[] chars = new char[text.length()];
		chars = text.toCharArray();
		int count=0;
		for (int i=0; i<chars.length; i++) {
			char c = chars[i];
			if (c!='\n' && c!='\t' && (c<32||c>127)) {
				count++;
				chars[i] = ' ';
			}
		}
		if (count>0)
			return new String(chars);
		else
			return text;
	}

}
