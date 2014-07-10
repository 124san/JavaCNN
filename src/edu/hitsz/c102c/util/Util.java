package edu.hitsz.c102c.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import edu.hitsz.c102c.cnn.Layer.Size;
import edu.hitsz.c102c.util.TimedTest.TestTask;

public class Util {

	/**
	 * �����ӦԪ�����ʱ��ÿ��Ԫ���ϵĲ���
	 * 
	 * @author jiqunpeng
	 * 
	 *         ����ʱ�䣺2014-7-9 ����9:28:35
	 */
	public interface Operator {
		public double process(double value);
	}

	// ����ÿ��Ԫ��value������1-value�Ĳ���
	public static final Operator one_value = new Operator() {
		@Override
		public double process(double value) {
			return 1 - value;
		}
	};

	// digmod����
	public static final Operator digmod = new Operator() {
		@Override
		public double process(double value) {
			return 1 / (1 + Math.pow(Math.E, -value));
		}
	};

	interface OperatorOnTwo {
		public double process(double a, double b);
	}

	/**
	 * ��������ӦԪ�صļӷ�����
	 */
	public static final OperatorOnTwo plus = new OperatorOnTwo() {
		@Override
		public double process(double a, double b) {
			return a + b;
		}
	};
	/**
	 * ��������ӦԪ�صĳ˷�����
	 */
	public static OperatorOnTwo multiply = new OperatorOnTwo() {
		@Override
		public double process(double a, double b) {
			return a * b;
		}
	};

	/**
	 * ��������ӦԪ�صļ�������
	 */
	public static OperatorOnTwo minus = new OperatorOnTwo() {
		@Override
		public double process(double a, double b) {
			return a - b;
		}
	};

	public static void printMatrix(double[][] matrix) {
		for (int i = 0; i < matrix.length; i++) {
			String line = Arrays.toString(matrix[i]);
			line = line.replaceAll(", ", "\t");
			System.out.println(line);
		}
		System.out.println();
	}

	/**
	 * �Ծ������180����ת,����matrix�ĸ����ϸ��ƣ������ԭ���ľ�������޸�
	 * 
	 * @param matrix
	 */
	public static double[][] rot180(double[][] matrix) {
		matrix = cloneMatrix(matrix);
		int m = matrix.length;
		int n = matrix[0].length;
		// ���жԳƽ��н���
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n / 2; j++) {
				double tmp = matrix[i][j];
				matrix[i][j] = matrix[i][n - 1 - j];
				matrix[i][n - 1 - j] = tmp;
			}
		}
		// ���жԳƽ��н���
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < m / 2; i++) {
				double tmp = matrix[i][j];
				matrix[i][j] = matrix[m - 1 - i][j];
				matrix[m - 1 - i][j] = tmp;
			}
		}
		return matrix;
	}

	private static Random r = new Random(2);

	/**
	 * �����ʼ������
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public static double[][] randomMatrix(int x, int y) {
		double[][] matrix = new double[x][y];
		for (int i = 0; i < x; i++) {
			for (int j = 0; j < y; j++) {
				// ���ֵ��[-0.05,0.05)֮�䣬��Ȩ�س�ʼ��ֵ��С���������ڱ�������
				matrix[i][j] = r.nextDouble() / 10 - 0.05;
			}
		}
		return matrix;
	}

	/**
	 * �����ʼ��һά����
	 * 
	 * @param len
	 * @return
	 */
	public static double[] randomArray(int len) {
		double[] data = new double[len];
		for (int i = 0; i < len; i++) {
			data[i] = r.nextDouble() / 10 - 0.05;
		}
		return data;
	}

	/**
	 * ������еĳ����������ȡbatchSize��[0,size)����
	 * 
	 * @param size
	 * @param batchSize
	 * @return
	 */
	public static int[] randomPerm(int size, int batchSize) {
		Set<Integer> set = new HashSet<Integer>();
		while (set.size() < batchSize) {
			set.add(r.nextInt(size));
		}
		int[] randPerm = new int[batchSize];
		int i = 0;
		for (Integer value : set)
			randPerm[i++] = value;
		return randPerm;
	}

	/**
	 * ���ƾ���
	 * 
	 * @param matrix
	 * @return
	 */
	public static double[][] cloneMatrix(final double[][] matrix) {

		final int m = matrix.length;
		int n = matrix[0].length;
		final double[][] outMatrix = new double[m][n];

		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				outMatrix[i][j] = matrix[i][j];
			}
		}
		return outMatrix;
	}

	/**
	 * �Ե���������в���
	 * 
	 * @param ma
	 * @param operator
	 * @return
	 */
	public static double[][] matrixOp(final double[][] ma, Operator operator) {
		final int m = ma.length;
		int n = ma[0].length;
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				ma[i][j] = operator.process(ma[i][j]);
			}
		}
		return ma;

	}

	/**
	 * ����ά����ͬ�ľ����ӦԪ�ز���,�õ��Ľ������mb�У���mb[i][j] = (op_a
	 * ma[i][j]) op (op_b mb[i][j])
	 * 
	 * @param ma
	 * @param mb
	 * @param operatorB
	 *            �ڵ�mb�����ϵĲ���
	 * @param operatorA
	 *            ��ma����Ԫ���ϵĲ���
	 * @return
	 * 
	 */
	public static double[][] matrixOp(final double[][] ma, final double[][] mb,
			final Operator operatorA, final Operator operatorB,
			OperatorOnTwo operator) {
		final int m = ma.length;
		int n = ma[0].length;
		if (m != mb.length || n != mb[0].length)
			throw new RuntimeException("���������С��һ��");

		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				double a = ma[i][j];
				if (operatorA != null)
					a = operatorA.process(a);
				double b = mb[i][j];
				if (operatorB != null)
					b = operatorB.process(b);
				mb[i][j] = operator.process(a, b);
			}
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
	public static double[][] kronecker(final double[][] matrix, final Size scale) {
		final int m = matrix.length;
		int n = matrix[0].length;
		final double[][] outMatrix = new double[m * scale.x][n * scale.y];

		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				for (int ki = i * scale.x; ki < (i + 1) * scale.x; ki++) {
					for (int kj = j * scale.y; kj < (j + 1) * scale.y; kj++) {
						outMatrix[ki][kj] = matrix[i][j];
					}
				}
			}
		}
		return outMatrix;
	}

	/**
	 * �Ծ�����о�ֵ��С
	 * 
	 * @param matrix
	 * @param scaleSize
	 * @return
	 */
	public static double[][] scaleMatrix(final double[][] matrix,
			final Size scale) {
		int m = matrix.length;
		int n = matrix[0].length;
		final int sm = m / scale.x;
		final int sn = n / scale.y;
		final double[][] outMatrix = new double[sm][sn];
		if (sm * scale.x != m || sn * scale.y != n)
			throw new RuntimeException("scale��������matrix");
		final int size = scale.x * scale.y;
		for (int i = 0; i < sm; i++) {
			for (int j = 0; j < sn; j++) {
				double sum = 0.0;
				for (int si = i * scale.x; si < (i + 1) * scale.x; si++) {
					for (int sj = j * scale.y; sj < (j + 1) * scale.y; sj++) {
						sum += matrix[si][sj];
					}
				}
				outMatrix[i][j] = sum / size;
			}
		}
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

		for (int i = 0; i < kms; i++) {
			for (int j = 0; j < kns; j++) {
				double sum = 0.0;
				for (int ki = 0; ki < km; ki++) {
					for (int kj = 0; kj < kn; kj++)
						sum += matrix[i + ki][j + kj] * kernel[ki][kj];
				}
				outMatrix[i][j] = sum;

			}
		}
		return outMatrix;

	}

	public static double sigmod(double x) {
		return 1 / (Math.pow(Math.E, -x));
	}

	/**
	 * ���Ծ��,���Խ����4���²������еľ����߲���2��
	 */
	private static void testConvn() {
		int count = 1;
		double[][] m = new double[5][5];
		for (int i = 0; i < m.length; i++)
			for (int j = 0; j < m[0].length; j++)
				m[i][j] = count++;
		double[][] k = new double[3][3];
		for (int i = 0; i < k.length; i++)
			for (int j = 0; j < k[0].length; j++)
				k[i][j] = 1;
		double[][] out;
		// out= convnValid(m, k);
		Util.printMatrix(m);
		out = convnFull(m, k);
		Util.printMatrix(out);
		// System.out.println();
		// out = convnFull(m, Util.rot180(k));
		// Util.printMatrix(out);

	}

	private static void testScaleMatrix() {
		int count = 1;
		double[][] m = new double[16][16];
		for (int i = 0; i < m.length; i++)
			for (int j = 0; j < m[0].length; j++)
				m[i][j] = count++;
		double[][] out = scaleMatrix(m, new Size(2, 2));
		Util.printMatrix(m);
		Util.printMatrix(out);
	}

	private static void testKronecker() {
		int count = 1;
		double[][] m = new double[5][5];
		for (int i = 0; i < m.length; i++)
			for (int j = 0; j < m[0].length; j++)
				m[i][j] = count++;
		double[][] out = kronecker(m, new Size(2, 2));
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
		double[][] out = matrixOp(m, k, new Operator() {

			@Override
			public double process(double value) {
				return value - 1;
			}
		}, new Operator() {

			@Override
			public double process(double value) {

				return -1 * value;
			}
		}, multiply);
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

	public static void testRot180() {
		double[][] matrix = { { 1, 2, 3, 4 }, { 4, 5, 6, 7 }, { 7, 8, 9, 10 } };
		printMatrix(matrix);
		rot180(matrix);
		System.out.println();
		printMatrix(matrix);
	}

	public static void main(String[] args) {
		new TimedTest(new TestTask() {

			@Override
			public void process() {
				testConvn();
				// testScaleMatrix();
				// testKronecker();
				// testMatrixProduct();
				// testCloneMatrix();
			}
		}, 1).test();
		ConcurenceRunner.stop();
	}

	/**
	 * �Ծ���Ԫ�����
	 * 
	 * @param error
	 * @return ע�������ͺܿ��ܻ����
	 */
	@Deprecated
	public static double sum(double[][] error) {
		int m = error.length;
		int n = error[0].length;
		double sum = 0.0;
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				sum += error[i][j];
			}
		}
		return sum;
	}

	/**
	 * ��errors[...][j]Ԫ�����
	 * 
	 * @param errors
	 * @param j
	 * @return
	 */
	public static double[][] sum(double[][][][] errors, int j) {
		int m = errors[0][j].length;
		int n = errors[0][j][0].length;
		double[][] result = new double[m][n];
		for (int mi = 0; mi < m; mi++) {
			for (int nj = 0; nj < n; nj++) {
				double sum = 0;
				for (int i = 0; i < errors.length; i++)
					sum += errors[i][j][mi][nj];
				result[mi][nj] = sum;
			}
		}

		return null;
	}
}
