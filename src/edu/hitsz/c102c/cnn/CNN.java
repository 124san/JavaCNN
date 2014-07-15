package edu.hitsz.c102c.cnn;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import edu.hitsz.c102c.cnn.Layer.Size;
import edu.hitsz.c102c.data.Dataset;
import edu.hitsz.c102c.data.Dataset.Record;
import edu.hitsz.c102c.util.ConcurenceRunner;
import edu.hitsz.c102c.util.ConcurenceRunner.Task;
import edu.hitsz.c102c.util.Log;
import edu.hitsz.c102c.util.Util;
import edu.hitsz.c102c.util.Util.Operator;

public class CNN {
	private static final double ALPHA = 1;
	protected static final double LAMBDA = 0;
	// ����ĸ���
	private List<Layer> layers;
	// ����
	private int layerNum;
	// ���й���
	private static ConcurenceRunner runner = new ConcurenceRunner();
	// �������µĴ�С
	private int batchSize;
	// �������������Ծ����ÿһ��Ԫ�س���һ��ֵ
	private Operator divide_batchSize;

	// �������������Ծ����ÿһ��Ԫ�س���alphaֵ
	private Operator multiply_alpha;

	// �������������Ծ����ÿһ��Ԫ�س���1-labmda*alphaֵ
	private Operator multiply_lambda;

	/**
	 * ��ʼ������
	 * 
	 * @param layerBuilder
	 *            �����
	 * @param inputMapSize
	 *            ����map�Ĵ�С
	 * @param classNum
	 *            ���ĸ�����Ҫ�����ݼ������ת��Ϊ0-classNum-1����ֵ
	 */
	public CNN(LayerBuilder layerBuilder, final int batchSize) {
		layers = layerBuilder.mLayers;
		layerNum = layers.size();
		this.batchSize = batchSize;
		setup(batchSize);
		initPerator();
	}

	/**
	 * ��ʼ��������
	 */
	private void initPerator() {
		divide_batchSize = new Operator() {

			@Override
			public double process(double value) {
				return value / batchSize;
			}

		};
		multiply_alpha = new Operator() {

			@Override
			public double process(double value) {

				return value * ALPHA;
			}

		};
		multiply_lambda = new Operator() {

			@Override
			public double process(double value) {

				return value * (1 - LAMBDA * ALPHA);
			}

		};
	}

	/**
	 * ��ѵ������ѵ������
	 * 
	 * @param trainset
	 * @param repeat
	 *            �����Ĵ���
	 */
	public void train(Dataset trainset, int repeat) {
		for (int t = 0; t < repeat; t++) {
			int epochsNum = 1 + trainset.size() / batchSize;// ���ȡһ�Σ�������ȡ��
			for (int i = 0; i < epochsNum; i++) {
				int[] randPerm = Util.randomPerm(trainset.size(), batchSize);				
				Layer.prepareForNewBatch();
				for (int index : randPerm) {
					train(trainset.getRecord(index));
					Layer.prepareForNewRecord();
				}
				// if (0 == 0)
				// return;
				// ����һ��batch�����Ȩ��
				updateParas();
				if (i % 50 == 0)
					Log.i("epochsNum " + epochsNum + ":" + i);
			}
			Log.i("begin test");
			Layer.prepareForNewBatch();
			double precision = test(trainset);
			Log.i("precision " + precision);
		}
	}

	/**
	 * ��������
	 * 
	 * @param trainset
	 * @return
	 */
	private double test(Dataset trainset) {
		Iterator<Record> iter = trainset.iter();
		int right = 0;
		int count = 0;
		while (iter.hasNext()) {
			Record record = iter.next();
			forward(record);
			Layer outputLayer = layers.get(layerNum - 1);
			int mapNum = outputLayer.getOutMapNum();
			double[] target = record.getDoubleEncodeTarget(mapNum);
			double[] out = new double[mapNum];
			for (int m = 0; m < mapNum; m++) {
				double[][] outmap = outputLayer.getMap(m);
				out[m] = outmap[0][0];
			}
			// if (record.getLable().intValue() ==
			// Util.getMaxIndex(out))
			// right++;
			if (isSame(out, target)) {
				right++;
				// if (right % 1000 == 0)
				// Log.i("out:" + Arrays.toString(out)
				// + " \n target:"
				// + Arrays.toString(target));
			}

			if (count++ % 1000 == 0)
				Log.i("out:" + Arrays.toString(out) + " \n target:"
						+ Arrays.toString(target));
		}
		return 1.0 * right / trainset.size();
	}

	/**
	 * Ԥ����
	 * 
	 * @param testset
	 * @param fileName
	 */
	public void predict(Dataset testset, String fileName) {
		Log.i("begin predict");
		try {
			int max = Layer.getClassNum();
			PrintWriter writer = new PrintWriter(new File(fileName));
			Iterator<Record> iter = testset.iter();
			while (iter.hasNext()) {
				Record record = iter.next();
				forward(record);
				Layer outputLayer = layers.get(layerNum - 1);

				int mapNum = outputLayer.getOutMapNum();
				double[] out = new double[mapNum];
				for (int m = 0; m < mapNum; m++) {
					double[][] outmap = outputLayer.getMap(m);
					out[m] = outmap[0][0];
				}
				int lable = Util.binaryArray2int(out);
				if (lable > max)
					lable = lable - (1 << (out.length - 1));
				writer.write(lable + "\n");
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		Log.i("end predict");
	}

	private boolean isSame(double[] output, double[] target) {
		boolean r = true;
		for (int i = 0; i < output.length; i++)
			if (Math.abs(output[i] - target[i]) > 0.5) {
				r = false;
				break;
			}

		return r;
	}

	private void train(Record record) {
		forward(record);
		backPropagation(record);
		// System.exit(0);
	}

	/*
	 * ������
	 */
	private void backPropagation(Record record) {
		setOutLayerErrors(record);
		setHiddenLayerErrors();
	}

	/**
	 * ���²���
	 */
	private void updateParas() {
		for (int l = 1; l < layerNum; l++) {
			Layer layer = layers.get(l);
			Layer lastLayer = layers.get(l - 1);
			switch (layer.getType()) {
			case conv:
			case output:
				updateKernels(layer, lastLayer);
				updateBias(layer, lastLayer);
				break;
			default:
				break;
			}
		}
	}

	/**
	 * ����ƫ��
	 * 
	 * @param layer
	 * @param lastLayer
	 */
	private void updateBias(Layer layer, Layer lastLayer) {
		double[][][][] errors = layer.getErrors();
		int mapNum = layer.getOutMapNum();
		for (int j = 0; j < mapNum; j++) {
			double[][] error = Util.sum(errors, j);
			// ����ƫ��
			double deltaBias = Util.sum(error) / batchSize;
			double bias = layer.getBias(j) + ALPHA * deltaBias;
			layer.setBias(j, bias);
		}
	}

	/**
	 * ����layer��ľ���ˣ�Ȩ�أ���ƫ��
	 * 
	 * @param layer
	 *            ��ǰ��
	 * @param lastLayer
	 *            ǰһ��
	 */
	private void updateKernels(Layer layer, Layer lastLayer) {
		int mapNum = layer.getOutMapNum();
		int lastMapNum = lastLayer.getOutMapNum();
		// double[][][][] errors = layer.getErrors();
		// double[][][][] lastMaps =
		// lastLayer.getMaps();
		for (int j = 0; j < mapNum; j++) {
			for (int i = 0; i < lastMapNum; i++) {
				// double[][] deltaKernel = Util
				// .convnValid(lastMaps, i, errors, j);
				// ��batch��ÿ����¼delta���
				double[][] deltaKernel = null;
				for (int r = 0; r < batchSize; r++) {
					double[][] error = layer.getError(r, j);
					if (deltaKernel == null)
						deltaKernel = Util.convnValid(lastLayer.getMap(r, i),
								error);
					else {// �ۻ����
						deltaKernel = Util.matrixOp(
								Util.convnValid(lastLayer.getMap(r, i), error),
								deltaKernel, null, null, Util.plus);
					}
				}

				// ����batchSize
				deltaKernel = Util.matrixOp(deltaKernel, divide_batchSize);
				// ���¾����
				double[][] kernel = layer.getKernel(i, j);
				deltaKernel = Util.matrixOp(kernel, deltaKernel,
						multiply_lambda, multiply_alpha, Util.plus);
				layer.setKernel(i, j, deltaKernel);
			}
		}
	}

	/**
	 * �����н�����Ĳв�
	 */
	private void setHiddenLayerErrors() {
		for (int l = layerNum - 2; l > 0; l--) {
			Layer layer = layers.get(l);
			Layer nextLayer = layers.get(l + 1);
			switch (layer.getType()) {
			case samp:
				setSampErrors(layer, nextLayer);
				break;
			case conv:
				setConvErrors(layer, nextLayer);
				break;
			default:// ֻ�в�����;������Ҫ����в�����û�вв������Ѿ������
				break;
			}
		}
	}

	/**
	 * ���ò�����Ĳв�
	 * 
	 * @param layer
	 * @param nextLayer
	 */
	private void setSampErrors(Layer layer, Layer nextLayer) {
		int mapNum = layer.getOutMapNum();
		final int nextMapNum = nextLayer.getOutMapNum();
		for (int i = 0; i < mapNum; i++) {
			double[][] sum = null;// ��ÿһ������������
			for (int j = 0; j < nextMapNum; j++) {
				double[][] nextError = nextLayer.getError(j);
				double[][] kernel = nextLayer.getKernel(i, j);
				// �Ծ���˽���180����ת��Ȼ�����fullģʽ�µþ��
				if (sum == null)
					sum = Util.convnFull(nextError, Util.rot180(kernel));
				else
					sum = Util.matrixOp(
							Util.convnFull(nextError, Util.rot180(kernel)),
							sum, null, null, Util.plus);
			}
			layer.setError(i, sum);
		}
	}

	/**
	 * ���þ����Ĳв�
	 * 
	 * @param layer
	 * @param nextLayer
	 */
	private void setConvErrors(final Layer layer, final Layer nextLayer) {
		// ��������һ��Ϊ�����㣬�������map������ͬ����һ��mapֻ����һ���һ��map���ӣ�
		// ���ֻ�轫��һ��Ĳв�kronecker��չ���õ������
		int mapNum = layer.getOutMapNum();
		for (int m = 0; m < mapNum; m++) {
			Size scale = nextLayer.getScaleSize();
			double[][] nextError = nextLayer.getError(m);
			double[][] map = layer.getMap(m);
			// ������ˣ����Եڶ��������ÿ��Ԫ��value����1-value����
			double[][] outMatrix = Util.matrixOp(map, Util.cloneMatrix(map),
					null, Util.one_value, Util.multiply);
			outMatrix = Util
					.matrixOp(outMatrix, Util.kronecker(nextError, scale),
							null, null, Util.multiply);
			layer.setError(m, outMatrix);
		}

	}

	/**
	 * ���������Ĳв�ֵ,�������񾭵�Ԫ�������٣��ݲ����Ƕ��߳�
	 * 
	 * @param record
	 */
	private void setOutLayerErrors(Record record) {
		
		Layer outputLayer = layers.get(layerNum - 1);
		int mapNum = outputLayer.getOutMapNum();
		 double[] target =
		 record.getDoubleEncodeTarget(mapNum);
		 for (int m = 0; m < mapNum; m++) {
		 double[][] outmap = outputLayer.getMap(m);
		 double output = outmap[0][0];
		 double errors = output * (1 - output) *
		 (target[m] - output);
		 outputLayer.setError(m, 0, 0, errors);
		 }
		 
//		double[] errors = new double[mapNum];
//		double[] outmaps = new double[mapNum];
//		for (int m = 0; m < mapNum; m++) {
//			double[][] outmap = outputLayer.getMap(m);
//			outmaps[m] = outmap[0][0];
//
//		}
//
//		errors[record.getLable().intValue()] = 1;
//		for (int m = 0; m < mapNum; m++) {
//			outputLayer.setError(m, 0, 0, outmaps[m] * (1 - outmaps[m])
//					* (errors[m] - outmaps[m]));
//		}
	}

	/**
	 * ǰ�����һ����¼
	 * 
	 * @param record
	 */
	private void forward(Record record) {
		// ����������map
		setInLayerOutput(record);
		for (int l = 1; l < layers.size(); l++) {
			Layer layer = layers.get(l);
			Layer lastLayer = layers.get(l - 1);
			switch (layer.getType()) {
			case conv:// ������������
				setConvOutput(layer, lastLayer);
				break;
			case samp:// �������������
				setSampOutput(layer, lastLayer);
				break;
			case output:// �������������,�������һ������ľ����
				setConvOutput(layer, lastLayer);
				break;
			default:
				break;
			}
		}
	}

	/**
	 * ���ݼ�¼ֵ���������������ֵ
	 * 
	 * @param record
	 */
	private void setInLayerOutput(Record record) {
		final Layer inputLayer = layers.get(0);
		final Size mapSize = inputLayer.getMapSize();
		final double[] attr = record.getAttrs();
		if (attr.length != mapSize.x * mapSize.y)
			throw new RuntimeException("���ݼ�¼�Ĵ�С�붨���map��С��һ��!");
		for (int i = 0; i < mapSize.x; i++) {
			for (int j = 0; j < mapSize.y; j++) {
				// ����¼���Ե�һά����Ū�ɶ�ά����
				inputLayer.setMapValue(0, i, j, attr[mapSize.x * i + j]);
			}
		}
	}

	/*
	 * �����������ֵ,ÿ���̸߳���һ����map
	 */
	private void setConvOutput(final Layer layer, final Layer lastLayer) {
		int mapNum = layer.getOutMapNum();
		final int lastMapNum = lastLayer.getOutMapNum();
		for (int j = 0; j < mapNum; j++) {
			double[][] sum = null;// ��ÿһ������map�ľ���������
			for (int i = 0; i < lastMapNum; i++) {
				double[][] lastMap = lastLayer.getMap(i);
				double[][] kernel = layer.getKernel(i, j);
				if (sum == null)
					sum = Util.convnValid(lastMap, kernel);
				else
					sum = Util.matrixOp(Util.convnValid(lastMap, kernel), sum,
							null, null, Util.plus);
			}
			final double bias = layer.getBias(j);
			sum = Util.matrixOp(sum, new Operator() {

				@Override
				public double process(double value) {
					return Util.sigmod(value + bias);
				}

			});
			if (sum[0][0] > 1)
				Log.i(sum[0][0] + "");
			layer.setMapValue(j, sum);
		}

	}

	/**
	 * ���ò���������ֵ���������ǶԾ����ľ�ֵ����
	 * 
	 * @param layer
	 * @param lastLayer
	 */
	private void setSampOutput(final Layer layer, final Layer lastLayer) {
		int lastMapNum = lastLayer.getOutMapNum();
		for (int i = 0; i < lastMapNum; i++) {
			double[][] lastMap = lastLayer.getMap(i);
			Size scaleSize = layer.getScaleSize();
			// ��scaleSize������о�ֵ����
			double[][] sampMatrix = Util.scaleMatrix(lastMap, scaleSize);
			layer.setMapValue(i, sampMatrix);
		}
	}

	/**
	 * ����cnn�����ÿһ��Ĳ���
	 * 
	 * @param batchSize
	 *            * @param classNum
	 * @param inputMapSize
	 */
	public void setup(int batchSize) {
		Layer inputLayer = layers.get(0);
		// ÿһ�㶼��Ҫ��ʼ�����map
		inputLayer.initOutmaps(batchSize);
		for (int i = 1; i < layers.size(); i++) {
			Layer layer = layers.get(i);
			Layer frontLayer = layers.get(i - 1);
			int frontMapNum = frontLayer.getOutMapNum();
			switch (layer.getType()) {
			case input:
				break;
			case conv:
				// ����map�Ĵ�С
				layer.setMapSize(frontLayer.getMapSize().subtract(
						layer.getKernelSize(), 1));
				// ��ʼ������ˣ�����frontMapNum*outMapNum�������
				layer.initKerkel(frontMapNum);
				// ��ʼ��ƫ�ã�����frontMapNum*outMapNum��ƫ��
				layer.initBias(frontMapNum);
				// batch��ÿ����¼��Ҫ����һ�ݲв�
				layer.initErros(batchSize);
				// ÿһ�㶼��Ҫ��ʼ�����map
				layer.initOutmaps(batchSize);
				break;
			case samp:
				// �������map��������һ����ͬ
				layer.setOutMapNum(frontMapNum);
				// ������map�Ĵ�С����һ��map�Ĵ�С����scale��С
				layer.setMapSize(frontLayer.getMapSize().divide(
						layer.getScaleSize()));
				// batch��ÿ����¼��Ҫ����һ�ݲв�
				layer.initErros(batchSize);
				// ÿһ�㶼��Ҫ��ʼ�����map
				layer.initOutmaps(batchSize);
				break;
			case output:
				// ��ʼ��Ȩ�أ�����ˣ��������ľ���˴�СΪ��һ���map��С
				layer.initOutputKerkel(frontMapNum, frontLayer.getMapSize());
				// ��ʼ��ƫ�ã�����frontMapNum*outMapNum��ƫ��
				layer.initBias(frontMapNum);
				// batch��ÿ����¼��Ҫ����һ�ݲв�
				layer.initErros(batchSize);
				// ÿһ�㶼��Ҫ��ʼ�����map
				layer.initOutmaps(batchSize);
				break;
			}
		}
	}

	/**
	 * ������ģʽ�������,Ҫ�����ڶ������Ϊ�����������Ϊ�����
	 * 
	 * @author jiqunpeng
	 * 
	 *         ����ʱ�䣺2014-7-8 ����4:54:29
	 */
	public static class LayerBuilder {
		private List<Layer> mLayers;

		public LayerBuilder() {
			mLayers = new ArrayList<Layer>();
		}

		public LayerBuilder(Layer layer) {
			this();
			mLayers.add(layer);
		}

		public LayerBuilder addLayer(Layer layer) {
			mLayers.add(layer);
			return this;
		}
	}

	/**
	 * �ȴ�
	 * 
	 * @param gate
	 */

	private static void await(CountDownLatch gate) {
		try {
			gate.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
