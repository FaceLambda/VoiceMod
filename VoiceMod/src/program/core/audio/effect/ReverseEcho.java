package program.core.audio.effect;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import program.core.audio.AudioEffect;
import program.core.audio.Channel;
import program.core.audio.Frame;

public class ReverseEcho implements AudioEffect {
	private LinkedList<double[][]> dat;
	private double scale;
	private double minScale;
	private float time;
	private int timeDom;
	private float peak;
	private double integrationFactor;
	private String channel;

	EffectPanel gui;

	public ReverseEcho(float time, float minScale, float peak, String channel) {
		dat = new LinkedList<double[][]>();
		this.peak = peak;
		this.minScale = minScale;
		this.channel = channel;
		setTime(time);
		genGui();
	}

	public ReverseEcho() {
		this(2, .1f, 5, "main");
	}

	public void setTime(float time) {
		this.time = time;
		timeDom = (int) Math.floor(time * Frame.getBaseFrequency() * 2);
		scale = Math.pow(10, Math.log(minScale) / timeDom);
		// integrate [scale^x from 0 to timeDom] to find area so we can average
		// properly
		integrationFactor = Math.log(scale);
		integrationFactor = Math.pow(scale, timeDom) / integrationFactor - (1 / integrationFactor);
		// remove or add empty samples to simulate an absent echo until the
		// audio stream is initiated
		while (dat.size() < timeDom) {
			dat.addFirst(new double[2][1]);
		}
		while (dat.size() > timeDom) {
			dat.removeFirst();
		}
	}

	private void genGui() {
		gui = AudioEffect.super.getGUI();
		JSlider sliderPeak;
		gui.add(sliderPeak = EffectPanel.genSlider(1, 75, 300, 50, "Peak", 0, 500, (int) (peak * 10), 10, 50, false));
		JLabel identifier = new JLabel("Peak: " + (sliderPeak.getValue() / 10f));
		identifier.setHorizontalAlignment(JLabel.CENTER);
		identifier.setBounds(1, 50, 300, 25);
		sliderPeak.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				peak = sliderPeak.getValue() / 10f;
				identifier.setText("Peak: " + (sliderPeak.getValue() / 10f));
			}
		});
		gui.add(identifier);

		JSlider sliderTime;
		gui.add(sliderTime = EffectPanel.genSlider(1, 155, 300, 50, "DecayTime", 0, 500, (int) (time * 100), 10, 100,
				false));
		JLabel identifierA = new JLabel("Decay Time: " + (sliderTime.getValue() / 100f) + "s");
		identifierA.setHorizontalAlignment(JLabel.CENTER);
		identifierA.setBounds(1, 130, 300, 25);
		sliderTime.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				setTime(sliderTime.getValue() / 100f);
				identifierA.setText("Decay Time: " + (sliderTime.getValue() / 100f) + "s");
			}
		});
		gui.add(identifierA);
		JTextField channelField;
		gui.add(channelField = new JTextField(channel));
		channelField.setBounds(1, 235, 300, 25);
		JLabel identifierChannel = new JLabel("Channel: "+channelField.getText());
		identifierChannel.setHorizontalAlignment(JLabel.CENTER);
		identifierChannel.setBounds(1,210,300,25);
		channelField.getDocument().addDocumentListener(new DocumentListener(){
			public void changedUpdate(DocumentEvent e) {
				change();
			}
			public void removeUpdate(DocumentEvent e) {
				change();
			}
			public void insertUpdate(DocumentEvent e) {
				change();
			}
			public void change() {
				channel = channelField.getText();
				identifierChannel.setText("Channel: "+channel);
			}
		});
		channelField.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				channel = channelField.getText();
				identifierChannel.setText("Channel: "+channel);
			}
		});
		gui.add(identifierChannel);
		//
		// JSlider minScaleS;
		// gui.add(minScaleS = EffectPanel.genSlider(1, 235, 300, 50,
		// "MinScale", 0, 400, (int)(Math.max(0,Math.log10(scale)*100+400))
		// , 10, 100, false));
		// System.out.println(scale+"/"+minScaleS.getValue());
		// JLabel identifierB = new JLabel("Minimum Scale:
		// 10^"+((minScaleS.getValue()-400)/100f)+" or "+
		// Math.pow(10, (minScaleS.getValue()-400)/100f));
		// identifierB.setHorizontalAlignment(JLabel.CENTER);
		// identifierB.setBounds(1,210,300,25);
		// minScaleS.addChangeListener(new ChangeListener(){
		// @Override
		// public void stateChanged(ChangeEvent e) {
		// float val = (minScaleS.getValue()-400)/100f;
		// minScale = (float) Math.pow(10, val);
		// setTime(time);
		// identifierB.setText("Minimum Scale: 10^"+val+" or "+
		// (float) Math.pow(10, val));
		// }
		// });
		// gui.add(identifierB);
	}

	@Override
	public void pass(Frame f) {
		Channel c = f.getSubChannel(channel);
		if(c == null){
			return;
		}
		c.decompose();
		Object[] array = dat.toArray();
		double[][] thisFrame = new double[2][Frame.getSamples()];
		for (int t = 0; t < array.length; t++) {
			double[][] sample = (double[][]) array[t];
			if (sample[0].length != Frame.getSamples()) {
				continue;
			}
			double scaleFactor = Math.pow(scale, t + 1);// maybe implement this
														// into standard echoes
														// as
														// pow(scale,timeDom-t)
			for (int i = 0; i < sample[0].length; i++) {
				// get the largest frequency sample overall to add to the stream

				// METHOD 1: MAXING
//				thisFrame[0][i] = Math.max(thisFrame[0][i], sample[0][i] * scaleFactor);
//				thisFrame[1][i] = Math.max(thisFrame[1][i], sample[1][i] * scaleFactor);

				// METHOD 2: AVERAGING
				thisFrame[0][i] += sample[0][i]*scaleFactor/integrationFactor;
				thisFrame[1][i] += sample[1][i]*scaleFactor/integrationFactor;
			}
		}
		// update the buffer, adding the newest and removing the oldest
		dat.pollFirst();
		double[][] mag = new double[2][thisFrame[0].length];
		for (int i = 0; i < mag[0].length; i++) {
			mag[0][i] = Math.min(c.getMagnitude(0, i), peak);
			mag[1][i] = Math.min(c.getMagnitude(1, i), peak);
			c.setMagnitude(0, i, thisFrame[0][i]);
			c.setMagnitude(1, i, thisFrame[1][i]);
		}
		dat.addLast(mag);
	}

	@Override
	public String getName() {
		return "Reverse Echo";
	}

	@Override
	public boolean getDecomposedCompatible() {
		return true;
	}

	@Override
	public boolean getComposedCompatible() {
		return false;
	}

	@Override
	public EffectPanel getGUI() {
		return gui;
	}

	@Override
	public String saveToString() {
		return channel.replaceAll(":", "")+":"+peak+":"+time;
	}

	@Override
	public AudioEffect fromString(String s) {
		String[] val = s.split(":");
		return new ReverseEcho(Float.parseFloat(val[2]),.1f,Float.parseFloat(val[1]),val[0]);
	}
}
