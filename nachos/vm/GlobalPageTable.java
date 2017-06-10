package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

class GlobalPageTable extends HashMap<Pair, TranslationEntry> {
    GlobalPageTable() {
        invertedPageTable = new Pair[Machine.processor().getNumPhysPages()];
        freePages = new LinkedList<>();
        for (int i = 0; i < invertedPageTable.length; i++)
            freePages.add(i);
        pinnedPages = new HashSet<>();
        swapper = new Swapper();
    }

    // insert a page into global page table, if no place available, swap out a page to disk
    void insertPage(Pair pair, TranslationEntry entry) {
        lock.acquire();

        if (freePages.isEmpty()) {
            int ppn = selectVictim();
            Pair victim = invertedPageTable[ppn];
            TranslationEntry victimEntry = get(victim);
            swapper.swapFromMemoryToDisk(victim, victimEntry);
            entry.ppn = ppn;
        }
        else
            entry.ppn = freePages.removeFirst();
        entry.valid = true;
        put(pair, entry);
        invertedPageTable[entry.ppn] = pair;

//        checkTLB();

        lock.release();
    }

    // get a page from global page table, if the page is not in memory, swap in a page to memory
    TranslationEntry getPage(Pair pair) {
        lock.acquire();
        TranslationEntry entry = get(pair);
        if (entry != null && !entry.valid) {
            if (freePages.isEmpty()) {
                int ppn = selectVictim();
                Pair victim = invertedPageTable[ppn];
                TranslationEntry victimEntry = get(victim);
                swapper.swapFromMemoryToDisk(victim, victimEntry);
                entry.ppn = victimEntry.ppn;
            }
            else
                entry.ppn = freePages.removeFirst();
            swapper.swapFromDiskToMemory(pair, entry);
            put(pair, entry);
            invertedPageTable[entry.ppn] = pair;
        }

//        checkTLB();

        lock.release();
        return entry;
    }

    void removePage(Pair pair) {
        lock.acquire();
        TranslationEntry entry = get(pair);
        if (entry == null) {
            lock.release();
            return;
        }
        if (!entry.valid)
            swapper.freePageFromDisk(pair);
        if (invertedPageTable[entry.ppn] != null)
            freePages.add(entry.ppn);
        remove(pair);
        invertedPageTable[entry.ppn] = null;
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry tlbEntry = Machine.processor().readTLBEntry(i);
            if (tlbEntry.ppn == entry.ppn)
                tlbEntry.valid = false;
            Machine.processor().writeTLBEntry(i, tlbEntry);
        }

//        checkTLB();

        lock.release();
    }

    void pinPage(Pair pair) {
        pinnedPages.add(pair);
    }

    void unpinPage(Pair pair) {
        pinnedPages.remove(pair);
    }

    void synchronizeFromTLB(int pid) {
        Processor processor = Machine.processor();
        for (int i = 0; i < processor.getTLBSize(); i++) {
            TranslationEntry tlbEntry = Machine.processor().readTLBEntry(i);
            if (tlbEntry.valid) {
                TranslationEntry pageTableEntry = get(new Pair(pid, tlbEntry.vpn));
                pageTableEntry.readOnly = tlbEntry.readOnly;
                pageTableEntry.used = tlbEntry.used;
                pageTableEntry.dirty = tlbEntry.dirty;
            }
        }
    }

    void synchronizeToTLB(int pid) {
        Processor processor = Machine.processor();
        for (int i = 0; i < processor.getTLBSize(); i++) {
            TranslationEntry tlbEntry = Machine.processor().readTLBEntry(i);
            if (tlbEntry.valid) {
                TranslationEntry pageTableEntry = get(new Pair(pid, tlbEntry.vpn));
                tlbEntry.readOnly = pageTableEntry.readOnly;
                tlbEntry.used = pageTableEntry.used;
                tlbEntry.dirty = pageTableEntry.dirty;
                processor.writeTLBEntry(i, tlbEntry);
            }
        }
    }

    private int selectVictim() {
//        victim = Math.max(victim, invertedPageTable.length-1);
        while (true) {
            TranslationEntry entry = get(invertedPageTable[victim]);
            if (entry != null) {
                if (!entry.used && !pinnedPages.contains(invertedPageTable[victim])) {
                    int toEvict = victim;
                    victim = (victim + 1) % invertedPageTable.length;
                    return toEvict;
                }
                entry.used = false;
            }
            victim = (victim + 1) % invertedPageTable.length;
        }
    }

    public boolean isPageValid(int ppn) {
        TranslationEntry entry = get(invertedPageTable[ppn]);
        return entry != null && entry.valid;
    }

    public void terminate() {
        swapper.terminate();
    }

    public void checkTLB() {
        Processor processor = Machine.processor();
        for (int i = 0; i < processor.getTLBSize(); i++) {
            TranslationEntry tEntry = processor.readTLBEntry(i);
            TranslationEntry pEntry = get(new Pair(VMKernel.currentProcess().processID(), tEntry.vpn));
            if (tEntry.valid) {
                System.out.println(String.valueOf(tEntry.valid) + String.valueOf(pEntry.valid));
                System.out.println(String.valueOf(tEntry.vpn) + String.valueOf(pEntry.vpn));
                System.out.println(String.valueOf(tEntry.ppn) + String.valueOf(pEntry.ppn));
                Lib.assertTrue(pEntry.valid);
                Lib.assertTrue(tEntry.vpn == pEntry.vpn);
                Lib.assertTrue(tEntry.ppn == pEntry.ppn);

            }
        }
    }

    private int victim = 0;

    private HashSet<Pair> pinnedPages;

    private Pair[] invertedPageTable;

    private LinkedList<Integer> freePages;

    private Lock lock = new Lock();

    private Swapper swapper;
}
