/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.uros.citlab.module.types;

import com.achteck.misc.log.Logger;
import com.achteck.misc.util.StopWatch;
import de.planet.math.util.MatrixUtil;
import org.apache.commons.math3.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @param <Reco>
 * @param <Reference>
 * @author gundram
 */
public class PathCalculatorGraph<Reco, Reference> {

    private UpdateScheme updateScheme = UpdateScheme.LAZY;
    private boolean useProgressBar = false;
    private static final Logger LOG = Logger.getLogger(PathCalculatorGraph.class.getName());
    private final List<ICostCalculator<Reco, Reference>> costCalculators = new ArrayList<>();
    private final List<ICostCalculatorMulti<Reco, Reference>> costCalculatorsMutli = new ArrayList<>();
    private PathFilter<Reco, Reference> filter = null;
    private final Comparator<IDistance<Reco, Reference>> cmpCostsAcc = new Comparator<IDistance<Reco, Reference>>() {
        @Override
        public int compare(IDistance<Reco, Reference> o1, IDistance<Reco, Reference> o2) {
            int d = o1.compareTo(o2);
            if (d != 0) {
                return d;
            }
            if (o1 == o2) {
                return 0;
            }
            int d2 = Integer.compare(o2.getPoint()[0], o1.getPoint()[0]);
            if (d2 != 0) {
                return d2;
            }
            return Integer.compare(o2.getPoint()[1], o1.getPoint()[1]);
        }
    };

    public void resetCostCalculators() {
        costCalculators.clear();
        costCalculatorsMutli.clear();
    }

    public static interface ICostCalculator<Reco, Reference> {

        public void init(DistanceMat<Reco, Reference> mat, List<Reco> recos, List<Reference> refs);

        public IDistance<Reco, Reference> getNeighbour(int[] point);

    }

    public static interface ICostCalculatorMulti<Reco, Reference> {

        public void init(DistanceMat<Reco, Reference> mat, List<Reco> recos, List<Reference> refs);

        public List<IDistance<Reco, Reference>> getNeighbours(int[] point);

    }

    public static class CostCalculatorTranspose<Reco, Reference> implements ICostCalculator<Reco, Reference> {

        private final ICostCalculator<Reference, Reco> cc;
        private DistanceMat<Reference, Reco> mat;

        public CostCalculatorTranspose(ICostCalculator<Reference, Reco> cc) {
            this.cc = cc;
        }

        @Override
        public void init(DistanceMat<Reco, Reference> mat, List<Reco> recos, List<Reference> refs) {
            this.mat = new DistanceMatTranspose(mat);
            cc.init(this.mat, refs, recos);
        }

        private int[] transpose(int[] point) {
            return new int[]{point[1], point[0]};
        }

        @Override
        public IDistance<Reco, Reference> getNeighbour(int[] point) {
            IDistance<Reference, Reco> distance = cc.getNeighbour(transpose(point));
            return new Distance<>(distance.getManipulation(), distance.getCosts(), distance.getCostsAcc(), transpose(distance.getPoint()), distance.getPointPrevious(), distance.getReferences(), distance.getRecos());
        }

        private class DistanceMatTranspose<Reco, Reference> extends DistanceMat<Reco, Reference> {

            private final DistanceMat<Reco, Reference> matInner;

            public DistanceMatTranspose(DistanceMat<Reco, Reference> mat) {
                super(0, 0);
                matInner = mat;
            }

            @Override
            public int getSizeX() {
                return matInner.getSizeY();
            }

            @Override
            public int getSizeY() {
                return matInner.getSizeX();
            }

            @Override
            public IDistance get(int y, int x) {
                return matInner.get(x, y);
            }

            @Override
            public void set(int y, int x, IDistance distance) {
                matInner.set(x, y, distance);
            }

            @Override
            public List<IDistance<Reco, Reference>> getBestPath() {
                return matInner.getBestPath();
            }

            @Override
            public IDistance<Reco, Reference> getLastElement() {
                return matInner.getLastElement();
            }

            @Override
            public String toString() {
                return matInner.toString();
            }

            @Override
            public int hashCode() {
                return matInner.hashCode();
            }

        }

    }

    public void useProgressBar(boolean useProgressBar) {
        this.useProgressBar = useProgressBar;
    }

    public void addCostCalculator(ICostCalculator<Reco, Reference> costCalculator) {
        costCalculators.add(costCalculator);
    }

    public void addCostCalculator(ICostCalculatorMulti<Reco, Reference> costCalculator) {
        costCalculatorsMutli.add(costCalculator);
    }

    public void addCostCalculatorTransposed(ICostCalculator<Reference, Reco> costCalculator) {
        costCalculators.add(new CostCalculatorTranspose<>(costCalculator));
    }

    public void setFilter(PathFilter<Reco, Reference> filter) {
        this.filter = filter;
        if (filter != null) {
            filter.init(cmpCostsAcc);
        }

    }

    //    public static enum Manipulation {
//
//        INS, DEL, SUB, COR, SPECIAL;
//    }
    public void setUpdateScheme(UpdateScheme updateScheme) {
        this.updateScheme = updateScheme;
    }

    public static enum UpdateScheme {
        LAZY, ALL;
    }

    public static interface PathFilter<Reco, Reference> {

        public void init(Comparator<IDistance<Reco, Reference>> comparator);

        public boolean addDistance(IDistance<Reco, Reference> newDistance);

        public boolean followDistance(IDistance<Reco, Reference> bestDistance);
    }

    public static interface IDistance<Reco, Reference> extends Comparable<IDistance<Reco, Reference>> {

        public double getCosts();

        public double getCostsAcc();

        public Reco[] getRecos();

        public Reference[] getReferences();

        public String getManipulation();

        public int[] getPointPrevious();

        public int[] getPoint();

        public boolean equals(IDistance<Reco, Reference> obj);

    }

    public static class DistanceMat<Reco, Reference> {

        private final HashMap<Integer, IDistance<Reco, Reference>> distMap;
        private final int sizeY;
        private final int sizeX;

        public DistanceMat(int y, int x) {
            this.distMap = new LinkedHashMap<>();
            sizeY = y;
            sizeX = x;
        }

        public IDistance<Reco, Reference> get(int y, int x) {
            return distMap.get(y * sizeX + x);
        }

        public IDistance<Reco, Reference> get(int[] pos) {
            return get(pos[0], pos[1]);
        }

        public void set(int y, int x, IDistance<Reco, Reference> distance) {
            distMap.put(y * sizeX + x, distance);
        }

        public void set(int[] position, IDistance<Reco, Reference> distance) {
            set(position[0], position[1], distance);
        }

        public int getSizeY() {
            return sizeY;
        }

        public int getSizeX() {
            return sizeX;
        }

        public IDistance<Reco, Reference> getLastElement() {
            return get(getSizeY() - 1, getSizeX() - 1);
        }

        public List<IDistance<Reco, Reference>> getBestPath() {
            IDistance<Reco, Reference> lastElement = getLastElement();
            if (lastElement == null) {
                LOG.log(Logger.WARN, "Distance Matrix not completely calculated.");
                return null;
            }
            LinkedList<IDistance<Reco, Reference>> res = new LinkedList<>();
            res.add(lastElement);
            int[] pos = lastElement.getPointPrevious();
            while (pos != null) {
                lastElement = get(pos[0], pos[1]);
                res.addFirst(lastElement);
                pos = lastElement.getPointPrevious();
            }
            res.removeFirst();
            return res;
        }

    }

    private int handleDistance(IDistance<Reco, Reference> distNew, DistanceMat<Reco, Reference> distMat, TreeSet<IDistance<Reco, Reference>> QSortedCostAcc, PathFilter<Reco, Reference> filter) {
        sw2.start();
        if (distNew == null) {
            sw2.stop();
            return 0;
        }
        int cnt = 0;
        final int[] posNew = distNew.getPoint();
        IDistance<Reco, Reference> distOld = distMat.get(posNew);
        boolean addDistance = filter == null || filter.addDistance(distNew);
        if (addDistance) {
            cnt++;
        }
        if (distOld == null) {
            if (addDistance) {
                distMat.set(posNew, distNew);
                int size = QSortedCostAcc.size();
                QSortedCostAcc.add(distNew);
                if (QSortedCostAcc.size() == size) {
                    throw new RuntimeException("error in using tree");
                }
            }
        } else if (cmpCostsAcc.compare(distNew, distOld) < 0) {
            if (addDistance) {
                distMat.set(posNew, distNew);
                QSortedCostAcc.remove(distOld);
                QSortedCostAcc.add(distNew);
            } else {
                distMat.set(posNew, null);
                QSortedCostAcc.remove(distOld);
            }

        }
        if (LOG.isTraceEnabled()) {
            if (distOld == null) {
                LOG.log(Logger.TRACE, "calculate at " + posNew[0] + ";" + posNew[1] + " distance " + distNew);
            } else if (cmpCostsAcc.compare(distNew, distOld) < 0) {
                LOG.log(Logger.TRACE, "calculate at " + posNew[0] + ";" + posNew[1] + " distance " + distNew);
            } else {
                LOG.log(Logger.TRACE, "calculate at " + posNew[0] + ";" + posNew[1] + " distance " + distOld);
            }
        }
        sw2.stop();
        return cnt;
    }

    StopWatch sw1 = new StopWatch();
    StopWatch sw2 = new StopWatch();

    public DistanceMat<Reco, Reference> calcDynProg(List<Reco> reco, List<Reference> ref, int maxCount) {
        if (ref == null || reco == null) {
            throw new RuntimeException("target or output is null");
        }
        boolean deleteFirstReco = false;
        if (reco.isEmpty() || reco.get(0) != null) {
            try {
                reco.add(0, null);
            } catch (UnsupportedOperationException ex) {
                reco = new LinkedList<>(reco);
                reco.add(0, null);
            }
            deleteFirstReco = true;
        }
        boolean deleteFirstRef = false;
        if (ref.isEmpty() || ref.get(0) != null) {
            try {
                ref.add(0, null);
            } catch (UnsupportedOperationException ex) {
                ref = new LinkedList<>(ref);
                ref.add(0, null);
            }
            deleteFirstRef = true;
        }
        int recoLength = reco.size();
        int refLength = ref.size();
        DistanceMat<Reco, Reference> distMat = new DistanceMat<>(recoLength, refLength);
//        IDistance<Reco, Reference> distanceInfinity = new Distance<>(null, null, 0, Double.MAX_VALUE, null);
//        LinkedList<IDistance<Reco, Reference>> candidates = new LinkedList<>();
        for (ICostCalculator<Reco, Reference> costCalculator : costCalculators) {
            costCalculator.init(distMat, reco, ref);
        }
        for (ICostCalculatorMulti<Reco, Reference> costCalculator : costCalculatorsMutli) {
            costCalculator.init(distMat, reco, ref);
        }
        int cnt = 0;
        TreeSet<IDistance<Reco, Reference>> QSortedCostAcc = new TreeSet<>(cmpCostsAcc);
//        HashSet<IDistance<Reco, Reference>> G = new LinkedHashSet<>();
        int[] startPoint = new int[]{0, 0};
        Distance<Reco, Reference> start = new Distance(null, 0, 0, startPoint, null, null, null);
        distMat.set(startPoint, start);
        QSortedCostAcc.add(start);
//        G.add(start);
//        distMat.set(startPoint, start);
        Pair<JFrame, JProgressBar> bar = useProgressBar ? getProgressBar("calculating Dynamic Matrix") : null;
        while (!QSortedCostAcc.isEmpty()) {
            sw1.start();
            IDistance<Reco, Reference> pollLastEntry = QSortedCostAcc.pollFirst();
            if (updateScheme.equals(UpdateScheme.LAZY) && distMat.getLastElement() == pollLastEntry) {
                sw1.stop();
                break;
            }
            if (filter != null && !filter.followDistance(pollLastEntry)) {
                sw1.stop();
                continue;
            }
            final int[] pos = pollLastEntry.getPoint();
            if (bar != null) {
                int newProcess = (int) Math.round(((double) pos[0]) * pos[1] / recoLength / refLength * 1000);
                if (bar.getSecond().getValue() < newProcess) {
                    bar.getSecond().setValue(newProcess);
                }
            }
            if (cnt % 10000 == 0) {
                LOG.log(Logger.DEBUG, String.format("on point [%4.1f%%,%4.1f%%] of [%5d,%5d] (count = %d)", pos[0] * 100.0 / recoLength, pos[1] * 100.0 / refLength, recoLength, refLength, cnt));
            }
            if (cnt >= maxCount) {
                LOG.log(Logger.DEBUG, String.format("found count = %d, return with so far calculated dynProg.", cnt));
                return distMat;
            }
            //all Neighbours v of u
            for (ICostCalculator<Reco, Reference> costCalculator : costCalculators) {
                IDistance<Reco, Reference> distance = costCalculator.getNeighbour(pos);
                cnt += handleDistance(distance, distMat, QSortedCostAcc, filter);
            }
            for (ICostCalculatorMulti<Reco, Reference> costCalculator : costCalculatorsMutli) {
                List<IDistance<Reco, Reference>> distances = costCalculator.getNeighbours(pos);
                if (distances == null) {
                    sw1.stop();
                    continue;
                }
                for (IDistance<Reco, Reference> distance : distances) {
                    cnt += handleDistance(distance, distMat, QSortedCostAcc, filter);
                }
            }
            sw1.stop();
        }
        if (bar != null) {
            bar.getSecond().setValue(1000);
        }
        if (distMat.getLastElement() == null) {
            LOG.log(Logger.WARN, "no path found from start to end with given cost calulators");
        }
        if (LOG.isDebugEnabled()) {
            LOG.log(Logger.DEBUG, "caculate " + cnt + " edges for " + ((reco.size() - 1) * (ref.size() - 1)) + " verticies");
        }
        if (LOG.isTraceEnabled() && false) {
            {
//                try {
//                    Reco get = reco.get(0);
//                    if (get.toString().length() > 1) {
                System.out.println("reco:" + reco.toString());
                System.out.println("ref: " + ref.toString());
                {
                    double[][] out = new double[distMat.getSizeY()][distMat.getSizeX()];
                    for (int i = 0; i < distMat.getSizeY(); i++) {
                        double[] ds = out[i];
                        for (int j = 0; j < distMat.getSizeX(); j++) {
                            ds[j] = distMat.get(i, j).getCosts();
                        }
                    }
                    System.out.println("ERROR");
                    dump(out);
                }
                double[][] out = new double[distMat.getSizeY()][distMat.getSizeX()];
                for (int i = 0; i < distMat.getSizeY(); i++) {
                    double[] ds = out[i];
                    for (int j = 0; j < distMat.getSizeX(); j++) {
                        ds[j] = distMat.get(i, j).getCostsAcc();
                    }
                }
                System.out.println("ACC");
                dump(out);
//                    }
//                } catch (RuntimeException ex) {

//                }
            }
        }
        if (deleteFirstReco) {
            reco.remove(0);
        }
        if (deleteFirstRef) {
            ref.remove(0);
        }
        if (bar != null) {
            bar.getFirst().setVisible(false);
            bar.getFirst().dispose();
        }
        LOG.log(Logger.DEBUG, "stopwatch 1: " + sw1.toString() + sw1.getCumulatedMillis());
        LOG.log(Logger.DEBUG, "stopwatch 2: " + sw2.toString() + sw2.getCumulatedMillis());
        return distMat;

    }

    private static void dump(double[][] mat) {
        for (int i = 0; i < mat.length; i++) {
            double[] doubles = mat[i];
            StringBuilder sb = new StringBuilder();
            for (double d : doubles) {
                sb.append(String.format("%8.3f", d));
            }
            System.out.println(sb.toString());
        }
    }

    public List<IDistance<Reco, Reference>> calcBestPath(Reco[] reco, Reference[] ref, int maxCount) {
        return calcBestPath(Arrays.asList(reco), Arrays.asList(ref), maxCount);
    }

    public List<IDistance<Reco, Reference>> calcBestPath(List<Reco> reco, List<Reference> ref, int maxCount) {
        return calcDynProg(reco, ref, maxCount).getBestPath();
    }

    public List<IDistance<Reco, Reference>> calcBestPath(DistanceMat<Reco, Reference> distMat) {
        return distMat.getBestPath();
    }

    public double calcCosts(List<Reco> reco, List<Reference> ref, int maxCount) {
        return calcDynProg(reco, ref, maxCount).getLastElement().getCostsAcc();
    }

    public double calcCosts(Reco[] reco, Reference[] ref, int maxCount) {
        return calcDynProg(Arrays.asList(reco), Arrays.asList(ref), maxCount).getLastElement().getCostsAcc();
    }

    public static class Distance<Reco, Reference> implements IDistance<Reco, Reference> {

        //        private final Distance previousDistance;
        private final String manipulation;
        private final double costs;
        private final double costsAcc;
        private final int[] previous;
        private final int[] point;
        private final Reco[] recos;
        private final Reference[] references;

        public Distance(String manipulation, double costs, double costAcc, int[] point, int[] previous, Reco[] recos, Reference[] references) {
            this.manipulation = manipulation;
            this.costs = costs;
            this.previous = previous;
            this.costsAcc = costAcc;
            this.recos = recos;
            this.references = references;
            this.point = point;
        }

        @Override
        public double getCosts() {
            return costs;
        }

        @Override
        public double getCostsAcc() {
            return costsAcc;
        }

        @Override
        public Reference[] getReferences() {
            return references;
        }

        @Override
        public Reco[] getRecos() {
            return recos;
        }

        @Override
        public String getManipulation() {
            return manipulation;
        }

        @Override
        public int[] getPoint() {
            return point;
        }

        @Override
        public int[] getPointPrevious() {
            return previous;
        }

        @Override
        public String toString() {
            return "cost=" + costs + ";manipulation=" + manipulation + ";costAcc=" + costsAcc + ";" + (recos.length == 0 ? "[]" : recos.length == 1 ? recos[0] : "ANZ=" + recos.length) + ";" + (references.length == 0 ? "[]" : references.length == 1 ? references[0] : "ANZ=" + references.length);
        }

        //        @Override
//        public int compareTo(IDistance<Reco, Reference> o) {
//            return Double.compare(getCostsAcc(), o.getCostsAcc());
//        }
        @Override
        public boolean equals(IDistance<Reco, Reference> obj) {
            return obj == this;
        }

        @Override
        public int compareTo(IDistance<Reco, Reference> o) {
            return Double.compare(costsAcc, o.getCostsAcc());
        }
    }

    public static Pair<JFrame, JProgressBar> getProgressBar(String title) {
        JFrame meinJFrame = new JFrame();
        meinJFrame.setSize(400, 100);
        meinJFrame.setTitle(title);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        JPanel meinPanel = new JPanel();

        // JProgressBar-Objekt wird erzeugt
        JProgressBar meinLadebalken = new JProgressBar(0, 1000);

        // Wert für den Ladebalken wird gesetzt
        meinLadebalken.setValue(0);
        // Der aktuelle Wert wird als 
        // Text in Prozent angezeigt
        meinLadebalken.setStringPainted(true);

        // JProgressBar wird Panel hinzugefügt
        meinPanel.add(meinLadebalken);
        meinJFrame.add(meinPanel);
        meinJFrame.setLocation(new Point((int) (screenSize.getWidth() - meinJFrame.getWidth()) / 2, (int) (screenSize.getHeight() - meinJFrame.getHeight()) / 2));
        meinJFrame.setVisible(true);
        return new Pair(meinJFrame, meinLadebalken);

    }
}
