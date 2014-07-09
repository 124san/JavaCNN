package edu.hitsz.c102c.cnn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import edu.hitsz.c102c.cnn.Layer.Size;
import edu.hitsz.c102c.data.Dataset;
import edu.hitsz.c102c.data.Dataset.Record;
import edu.hitsz.c102c.util.ConcurenceRunner;
import edu.hitsz.c102c.util.ConcurenceRunner.Task;
import edu.hitsz.c102c.util.TimedTest;
import edu.hitsz.c102c.util.TimedTest.TestTask;
import edu.hitsz.c102c.util.Util;

public class CNN {
	// ����ĸ���
	private List<Layer> layers;
	// ����
	private int layerNum;
	// ���й���
	private static ConcurenceRunner runner = new ConcurenceRunner();

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
	public CNN(LayerBuilder layerBuilder, int batchSize) {
		layers = layerBuilder.mLayers;
		layerNum = layers.size();
		setup(batchSize);
	}

	/**
	 * ��ѵ������ѵ������
	 * 
	 * @param trainset
	 */
	public void train(Dataset trainset, int batchSize) {
		int epochsNum = 1 + trainset.size() / batchSize;// ���ȡһ�Σ�������ȡ��
		for (int i = 0; i < epochsNum; i++) {
			int[] randPerm = Util.randomPerm(trainset.size(), batchSize);
			Layer.prepareForNewBatch();
			for (int index : randPerm) {
				train(trainset.getRecord(index));
			}

		}
	}

	private void train(Record record) {
		forward(record);
		backPropagation(record);
	}

	/*
	 * ������
	 */
	private void backPropagation(Record record) {
		setOutLayerErrors(record);
		setHiddenLayerErros();

	}

	/**
	 * �����н�����Ĳв�
	 */
	private void setHiddenLayerErros() {
		for (int l = layerNum - 2; l > 0; l--) {
			Layer layer = layers.get(l);
			int mapNum = layer.getOutMapNum();
			Layer nextLayer = layers.get(l + 1);
			switch (layer.getType()) {
			case samp:

				break;
			case conv:
				// ��������һ��Ϊ�����㣬�������map������ͬ����һ��mapֻ����һ���һ��map���ӣ�
				// ���ֻ�轫��һ��Ĳв�kronecker��չ���õ������
				for (int m = 0; m < mapNum; m++) {
					Size scale = nextLayer.getScaleSize();
					double[][] nextError = nextLayer.getError(m);
					double[][] map = layer.getMap(m);
					// ������ˣ����Եڶ��������ÿ��Ԫ��value����1-value����
					matrixProduct(map, cloneMatrix(map), null, new Operator() {

						@Override
						public double process(double value) {
							return 1 - value;
						}

					});
					double[][] outMatrix = matrixProduct(map,
							kronecker(nextError, scale), null, null);

					layer.setError(m, outMatrix);

				}
				break;
			default:
				break;
			}
		}
	}

	/**
	 * ���������Ĳв�ֵ
	 * 
	 * @param record
	 */
	private void setOutLayerErrors(Record record) {
		Layer outputLayer = layers.get(layerNum - 1);
		int mapNum = outputLayer.getOutMapNum();
		double[] target = record.getDoubleEncodeTarget(mapNum);
		for (int m = 0; m < mapNum; m++) {
			double[][] outmap = outputLayer.getMap(m);
			double output = outmap[0][0];
			double errors = output * (1 - output) * (target[m] - output);
			outputLayer.setError(m, 0, 0, errors);
		}
	}

	/**
	 * ǰ�����һ����¼
	 * 
	 * @param record
	 */
	private void forward(Record record) {
		// ����������map
		Layer inputLayer = layers.get(0);
		Size mapSize = inputLayer.getMapSize();
		double[] attr = record.getAttrs();
		if (attr.length != mapSize.x * mapSize.y)
			throw new RuntimeException("���ݼ�¼�Ĵ�С�붨���map��С��һ��!");
		int index = 0;
		for (int i = 0; i < mapSize.x; i++)
			for (int j = 0; j < mapSize.y; j++) {
				double value = attr[index++];
				inputLayer.setMapValue(0, i, j, value);
			}
		for (int l = 1; l < layers.size(); l++) {
			Layer layer = layers.get(l);
			Layer lastLayer = layers.get(l - 1);
			int mapNum = layer.getOutMapNum();
			int lastMapNum = lastLayer.getOutMapNum();
			switch (layer.getType()) {
			case conv:// ������������
				for (int j = 0; j < mapNum; j++)
					for (int i = 0; i < lastMapNum; i++) {
						double[][] lastMap = lastLayer.getMap(i);
						double[][] kernel = layer.getKernel(i, j);
						double[][] outMatrix = convnValid(lastMap, kernel);
						layer.setMapValue(j, outMatrix);
					}
				break;
			case samp:// �������������
				for (int i = 0; i < lastMapNum; i++) {
					double[][] lastMap = lastLayer.getMap(i);
					Size scaleSize = layer.getScaleSize();
					double[][] sampMatrix = scaleMatrix(lastMap, scaleSize);
					layer.setMapValue(i, sampMatrix);
				}
				break;
			case output:// �������������
				for (int j = 0; j < mapNum; j++)
					for (int i = 0; i < lastMapNum; i++) {
						double[][] lastMap = lastLayer.getMap(i);
						double[][] kernel = layer.getKernel(i, j);
						double[][] outMatrix = convnValid(lastMap, kernel);
						layer.setMapValue(j, outMatrix);
					}
				break;
			default:
				break;
			}
		}
	}

	/**
	 * ����cnn�����ÿһ��Ĳ���
	 * 
	 * @param batchSize
	 * 
	 * @param classNum
	 * @param inputMapSize
	 */
	public void setup(int batchSize) {
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
				break;
			case samp:
				// �������map��������һ����ͬ
				layer.setOutMapNum(frontMapNum);
				// ������map�Ĵ�С����һ��map�Ĵ�С����scale��С
				layer.setMapSize(frontLayer.getMapSize().divide(
						layer.getScaleSize()));
				break;
			case output:
				// ��ʼ��Ȩ�أ�����ˣ�������frontMapNum*outMapNum��1*1�����
				layer.initKerkel(frontMapNum);
				// ��ʼ��ƫ�ã�����frontMapNum*outMapNum��ƫ��
				layer.initBias(frontMapNum);
				break;
			}
			// ÿһ�㶼��Ҫ��ʼ�����map
			layer.initOutmaps(batchSize);
		}
	}

	/**
	 * ������ģʽ�������,Ҫ�����ڶ������Ϊ�����������Ϊ�����
	 * 
	 * @author jiqunpeng
	 * 
	 *         ����ʱ�䣺2014-7-8 ����4:54:29
	 */
	class LayerBuilder {
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

	public static double[][] cloneMatrix(final double[][] matrix) {
		final int m = matrix.length;
		int n = matrix[0].length;
		final double[][] outMatrix = new double[m][n];
		int cpuNum = ConcurenceRunner.cpuNum;
		cpuNum = cpuNum < n ? cpuNum : 1;// ��cpu�ĸ���Сʱ��ֻ��һ���߳�
		int fregLength = (n + cpuNum - 1) / cpuNum;// ����ȡ��
		final CountDownLatch gate = new CountDownLatch(cpuNum);
		for (int cpu = 0; cpu < cpuNum; cpu++) {
			int start = cpu * fregLength;
			int tmp = (cpu + 1) * fregLength;
			int end = tmp <= n ? tmp : n;
			Task task = new Task(start, end) {

				@Override
				public void process(int start, int end) {
					for (int i = 0; i < m; i++) {
						for (int j = start; j < end; j++) {
							outMatrix[i][j] = matrix[i][j];
						}
					}
					gate.countDown();
				}

			};
			runner.run(task);
		}
		await(gate);
		return outMatrix;
	}

	/**
	 * ����ά����ͬ�ľ����ӦԪ�����,�õ��Ľ������mb�У���mb[i][j] =
	 * ma[i][j]*mb[i][j]
	 * 
	 * @param ma
	 * @param mb
	 * @param operatorB
	 *            �ڵ�mb�����ϵĲ���
	 * @param operatorA
	 *            ��ma����Ԫ���ϵĲ���
	 * @return
	 * @deprecated ���mb��������޸ģ���ע��
	 */
	private static double[][] matrixProduct(final double[][] ma,
			final double[][] mb, final Operator operatorA,
			final Operator operatorB) {
		final int m = ma.length;
		int n = ma[0].length;
		if (m != mb.length || n != mb[0].length)
			throw new RuntimeException("���������С��һ��");
		int cpuNum = ConcurenceRunner.cpuNum;
		cpuNum = cpuNum < n ? cpuNum : 1;// ��cpu�ĸ���Сʱ��ֻ��һ���߳�
		int fregLength = (n + cpuNum - 1) / cpuNum;// ����ȡ��
		final CountDownLatch gate = new CountDownLatch(cpuNum);
		for (int cpu = 0; cpu < cpuNum; cpu++) {
			int start = cpu * fregLength;
			int tmp = (cpu + 1) * fregLength;
			int end = tmp <= n ? tmp : n;
			Task task = new Task(start, end) {

				@Override
				public void process(int start, int end) {
					for (int i = 0; i < m; i++) {
						for (int j = start; j < end; j++) {
							double a = ma[i][j];
							if (operatorA != null)
								a = operatorA.process(a);
							double b = mb[i][j];
							if (operatorB != null)
								b = operatorB.process(b);
							mb[i][j] = a * b;
						}
					}
					gate.countDown();
				}

			};
			runner.run(task);
		}
		return mb;
	}

	/**
	 * �����ڿ˻�,�Ծ��������չ
	 * 
	 * @param matrix
	 * @param scale
	 * @return
	 */
	private static double[][] kronecker(final double[][] matrix,
			final Size scale) {
		final int m = matrix.length;
		int n = matrix[0].length;
		final double[][] outMatrix = new double[m * scale.x][n * scale.y];
		int cpuNum = ConcurenceRunner.cpuNum;
		cpuNum = cpuNum < n ? cpuNum : 1;// ��cpu�ĸ���Сʱ��ֻ��һ���߳�
		int fregLength = (n + cpuNum - 1) / cpuNum;// ����ȡ��
		final CountDownLatch gate = new CountDownLatch(cpuNum);
		for (int cpu = 0; cpu < cpuNum; cpu++) {
			int start = cpu * fregLength;
			int tmp = (cpu + 1) * fregLength;
			int end = tmp <= n ? tmp : n;
			Task task = new Task(start, end) {

				@Override
				public void process(int start, int end) {
					for (int i = 0; i < m; i++) {
						for (int j = start; j < end; j++) {
							for (int ki = i * scale.x; ki < (i + 1) * scale.x; ki++) {
								for (int kj = j * scale.y; kj < (j + 1)
										* scale.y; kj++) {
									outMatrix[ki][kj] = matrix[i][j];
								}
							}
						}
					}
					gate.countDown();
				}

			};
			runner.run(task);
		}
		await(gate);
		return outMatrix;
	}

	/**
	 * �Ծ��������С
	 * 
	 * @param matrix
	 * @param scaleSize
	 * @return
	 */
	private static double[][] scaleMatrix(final double[][] matrix,
			final Size scale) {
		int m = matrix.length;
		int n = matrix[0].length;
		final int sm = m / scale.x;
		final int sn = n / scale.y;
		final double[][] outMatrix = new double[sm][sn];
		if (sm * scale.x != m || sn * scale.y != n)
			throw new RuntimeException("scale��������matrix");
		// ��������
		int cpuNum = ConcurenceRunner.cpuNum;
		cpuNum = cpuNum < sn ? cpuNum : 1;// ��cpu�ĸ���Сʱ��ֻ��һ���߳�
		int fregLength = (sn + cpuNum - 1) / cpuNum;// ����ȡ��
		final CountDownLatch gate = new CountDownLatch(cpuNum);
		final int size = scale.x * scale.y;
		for (int cpu = 0; cpu < cpuNum; cpu++) {
			int start = cpu * fregLength;
			int tmp = (cpu + 1) * fregLength;
			int end = tmp <= sn ? tmp : sn;
			Task task = new Task(start, end) {
				@Override
				public void process(int start, int end) {
					for (int i = 0; i < sm; i++) {
						for (int j = start; j < end; j++) {
							double sum = 0.0;
							for (int si = i * scale.x; si < (i + 1) * scale.x; si++) {
								for (int sj = j * scale.y; sj < (j + 1)
										* scale.y; sj++) {
									sum += matrix[si][sj];
								}
							}
							outMatrix[i][j] = sum / size;
						}
					}
					gate.countDown();
				}
			};
			runner.run(task);

		}
		await(gate);
		return outMatrix;
	}

	/**
	 * ����fullģʽ�ľ��
	 * 
	 * @param matrix
	 * @param kernel
	 * @return
	 */
	public static double[][] convnFull(double[][] matrix,
			final double[][] kernel) {
		int m = matrix.length;
		int n = matrix[0].length;
		final int km = kernel.length;
		final int kn = kernel[0].length;
		// ��չ����
		final double[][] extendMatrix = new double[m + 2 * (km - 1)][n + 2
				* (kn - 1)];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++)
				extendMatrix[i + km - 1][j + kn - 1] = matrix[i][j];
		}
		return convnValid(extendMatrix, kernel);
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

	/**
	 * ����validģʽ�ľ��
	 * 
	 * @param matrix
	 * @param kernel
	 * @return
	 */
	public static double[][] convnValid(final double[][] matrix,
			final double[][] kernel) {
		int m = matrix.length;
		int n = matrix[0].length;
		final int km = kernel.length;
		final int kn = kernel[0].length;
		// ��Ҫ�����������
		int kns = n - kn + 1;
		// ��Ҫ�����������
		final int kms = m - km + 1;
		// �������
		final double[][] outMatrix = new double[kms][kns];
		// ��������
		int cpuNum = ConcurenceRunner.cpuNum;
		cpuNum = cpuNum < kns ? cpuNum : 1;// ��cpu�ĸ���Сʱ��ֻ��һ���߳�
		int fregLength = (kns + cpuNum - 1) / cpuNum;
		// Log.i("kns:" + kns);
		// Log.i("fregLength:" + fregLength);
		final CountDownLatch gate = new CountDownLatch(cpuNum);
		for (int cpu = 0; cpu < cpuNum; cpu++) {
			int start = cpu * fregLength;
			int tmp = (cpu + 1) * fregLength;
			int end = tmp <= kns ? tmp : kns;
			Task task = new Task(start, end) {

				@Override
				public void process(int start, int end) {

					for (int i = 0; i < kms; i++) {
						for (int j = start; j < end; j++) {
							double sum = 0.0;
							for (int ki = 0; ki < km; ki++) {
								for (int kj = 0; kj < kn; kj++)
									sum += matrix[i + ki][j + kj]
											* kernel[ki][kj];
							}
							outMatrix[i][j] = sum;

						}
					}
					gate.countDown();
				}

			};
			runner.run(task);

		}
		await(gate);
		return outMatrix;

	}

	/**
	 * ���Ծ��,���Խ����4���²������еľ����߲���2��
	 */
	private static void testConvn() {
		int count = 1;
		double[][] m = new double[5000][500];
		for (int i = 0; i < m.length; i++)
			for (int j = 0; j < m[0].length; j++)
				m[i][j] = count++;
		double[][] k = new double[1][1];
		for (int i = 0; i < k.length; i++)
			for (int j = 0; j < k[0].length; j++)
				k[i][j] = 1.5;
		double[][] out;
		// out= convnValid(m, k);
		// Util.printMatrix(m);
		out = convnFull(m, k);
		// Util.printMatrix(out);
		// System.out.println();
		// out = convnFull(m, Util.rot180(k));
		// Util.printMatrix(out);

	}

	private static void testScaleMatrix() {
		int count = 1;
		double[][] m = new double[20000][200];
		for (int i = 0; i < m.length; i++)
			for (int j = 0; j < m[0].length; j++)
				m[i][j] = 1;
		double[][] out = scaleMatrix(m, new Size(4, 4));
		// Util.printMatrix(m);
		// System.out.println();
		// Util.printMatrix(out);
	}

	private static void testKronecker() {
		int count = 1;
		double[][] m = new double[5][5];
		for (int i = 0; i < m.length; i++)
			for (int j = 0; j < m[0].length; j++)
				m[i][j] = count++;
		double[][] out = kronecker(m, new Size(1, 1));
		Util.printMatrix(m);
		System.out.println();
		Util.printMatrix(out);
	}

	private static void testMatrixProduct() {
		int count = 1;
		double[][] m = new double[5][5];
		for (int i = 0; i < m.length; i++)
			for (int j = 0; j < m[0].length; j++)
				m[i][j] = count++;
		double[][] k = new double[5][5];
		for (int i = 0; i < k.length; i++)
			for (int j = 0; j < k[0].length; j++)
				k[i][j] = j;

		Util.printMatrix(m);
		Util.printMatrix(k);
		double[][] out = matrixProduct(m, k, new Operator() {

			@Override
			public double process(double value) {

				return value - 1;
			}
		}, new Operator() {

			@Override
			public double process(double value) {

				return -1 * value;
			}
		});
		Util.printMatrix(out);
	}

	private static void testCloneMatrix() {
		int count = 1;
		double[][] m = new double[5][5];
		for (int i = 0; i < m.length; i++)
			for (int j = 0; j < m[0].length; j++)
				m[i][j] = count++;
		double[][] out = cloneMatrix(m);
		Util.printMatrix(m);

		Util.printMatrix(out);
	}

	public static void main(String[] args) {
		new TimedTest(new TestTask() {

			@Override
			public void process() {
				// testConvn();
				// testScaleMatrix();
				// testKronecker();
				testMatrixProduct();
				// testCloneMatrix();
			}
		}, 1).test();
		ConcurenceRunner.stop();
	}

	/**
	 * �����ӦԪ�����ʱ��ÿ��Ԫ���ϵĲ���
	 * 
	 * @author jiqunpeng
	 * 
	 *         ����ʱ�䣺2014-7-9 ����9:28:35
	 */
	interface Operator {
		public double process(double value);
	}
}
