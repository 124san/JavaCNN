package edu.hitsz.c102c.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class Util {

	public static void printMatrix(double[][] matrix) {
		for (int i = 0; i < matrix.length; i++) {
			String line = Arrays.toString(matrix[i]);
			line = line.replaceAll(", ", "\t");
			System.out.println(line);
		}
		System.out.println();
	}

	/**
	 * �Ծ������180����ת
	 * 
	 * @param matrix
	 */
	public static double[][] rot180(double[][] matrix) {
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

	public static void main(String[] args) {
		double[][] matrix = { { 1, 2, 3, 4 }, { 4, 5, 6, 7 }, { 7, 8, 9, 10 } };
		printMatrix(matrix);
		rot180(matrix);
		System.out.println();
		printMatrix(matrix);
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

	
}
