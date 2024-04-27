package luonq.test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import util.BaseUtils;
import util.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/2/9.
 */
public class Test {

    public static void main(String[] args) throws Exception {
        List<String> lineList = BaseUtils.readFile(Constants.TEST_PATH + "test");
        Map<String, Set<String>> grabYearMap = Maps.newHashMap();
        for (String line : lineList) {
            String[] split = line.split(" ");
            String stock = split[0];
            String year = split[1];
            int yearInt = Integer.valueOf(year);

            if (!grabYearMap.containsKey(stock)) {
                grabYearMap.put(stock, Sets.newHashSet());
            }
            grabYearMap.get(stock).add(year);
            //            grabYearMap.get(stock).add(String.valueOf(yearInt - 1));
            //            grabYearMap.get(stock).add(String.valueOf(yearInt + 1));
        }

        //        Map<String, Set<String>> grabRangeMap = Maps.newHashMap();
        for (String stock : grabYearMap.keySet()) {
            Set<String> grabYearSet = grabYearMap.get(stock);
            Set<String> grabRangeSet = Sets.newHashSet();
            for (String year : grabYearSet) {
                int yearInt = Integer.valueOf(year);
                grabRangeSet.add("01/01/" + (yearInt - 1) + "~" + "01/01/" + yearInt);
                grabRangeSet.add("01/01/" + (yearInt + 1) + "~" + "01/01/" + (yearInt + 2));
                grabRangeSet.add("01/01/" + year + "~" + "01/01/" + (yearInt + 1));
            }
            //            grabRangeMap.put(stock, grabRangeSet);
            String setStr = grabRangeSet.toString();
            System.out.println(stock + "\t" + setStr.substring(1, setStr.length() - 1));
        }
    }

    public static void computeGrabTimes() throws Exception {
        //        List<String> markets = Lists.newArrayList("XNAS-ADRC","XNYS-ADRC");
        //        List<String> markets = Lists.newArrayList("XNAS","XNYS");
        List<String> markets = Lists.newArrayList("XNAS");

        int size = 100;
        int sum = 0;
        long i = 1000 * 3600 * 24;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

        long nowTime = format.parse("2023-01-01").getTime();
        long _2000 = format.parse("2000-01-01").getTime();

        for (String market : markets) {
            Map<String, String> openMap = BaseUtils.getOpenData(market);
            for (String stock : openMap.keySet()) {
                String open = openMap.get(stock);
                Date openDate = format.parse(open);
                long openTime = openDate.getTime();
                if (openTime < _2000) {
                    openTime = _2000;
                }

                int count = Math.abs((int) ((nowTime - openTime) / i));
                //                System.out.println(stock + " " + count);
                sum += count;
                size--;
                if (size < 0) {
                    //                    break;
                }
            }
            System.out.println(market + " " + sum);
        }
    }

    private static void groupOpenDataByYear() throws Exception {
        Map<String, String> xnasOpenMap = BaseUtils.getOpenData("XNAS");

        Map<String/*year*/, Integer/*count*/> countMap = Maps.newHashMap();
        for (String code : xnasOpenMap.keySet()) {
            String open = xnasOpenMap.get(code);
            if (open.length() != 10) {
                continue;
            }
            String year = open.substring(0, 4);
            if (!countMap.containsKey(year)) {
                countMap.put(year, 0);
            }
            int count = countMap.get(year) + 1;
            countMap.put(year, count);
        }

        System.out.println(countMap);
    }

    private static void getOpenData() throws Exception {
        Map<String, String> xnasOpenMap = BaseUtils.getOpenData("XNAS");
        Map<String, String> xnysOpenMap = BaseUtils.getOpenData("XNYS");
        Map<String, String> allMap = Maps.newHashMap();
        allMap.putAll(xnasOpenMap);
        allMap.putAll(xnysOpenMap);

        List<String> stockList = Lists.newArrayList("hofv", "rick", "lmnl", "eton", "whf", "ades", "awre", "east", "fthm", "gray", "gsit", "bfst", "sohu", "ccb", "cgrn", "bioc", "bmra", "troo", "afri", "self", "icmb", "aezs", "bmea", "aout", "opy", "alr", "ccld", "cass", "itrn", "uvsp", "pcti", "bwmx", "tara", "morn", "mcft", "sldb", "voxx", "eldn", "uone", "taro", "indt", "amci", "bcda", "nrc", "acab", "abvc", "gure", "agil", "ltbr", "mrai", "ccap", "vtsi", "sxi", "lgmk", "jagx", "vsec", "airt", "turn", "wneb", "fenc", "fbiz", "ufcs", "tzoo", "ebon", "omga", "anip", "arow", "pays", "vinp", "csse", "thff", "bcbp", "brt", "unf", "mcbs", "esgr", "cfb", "ards", "irix", "imra", "pfc", "cbnk", "oblg", "essa", "ppih", "miro", "dmlp", "thch", "adal", "tcbk", "flux", "xbit", "cvgi", "sieb", "avtx", "tsat", "ka", "acnt", "ftek", "tyra", "mvbf", "slgg", "dcbo", "wash", "civb", "abio", "banx", "acac", "eq", "loan", "ulbi", "qlgn", "onew", "amsf", "drrx", "innv", "clpt", "epsn", "svvc", "bset", "frgi", "ttnp", "btwn", "byfc", "moxc", "cswi", "nwpx", "grow", "ugro", "sgbx", "wmk", "cvco", "brag", "quik", "snpo", "frba", "saft", "kltr", "ktcc", "krbp", "batra", "grtx", "mdwd", "adus", "fisi", "farm", "imkta", "agmh", "kvsa", "rdvt", "satl", "acax", "cac", "uht", "bfri", "yvr", "hmpt", "oesx", "msb", "afbi", "kmda", "ke", "legh", "asys", "srax", "che", "cznc", "linc", "dtea", "dfli", "avnw", "jjsf", "ccne", "bfin", "nspr", "ntwk", "refi", "aey", "aggr", "mbot", "inod", "sqft", "wtba", "pcyg", "uonek", "asrv", "dgica", "amot", "enz", "vani", "acxp", "aame", "csbr", "powl", "mrcc", "lmfa", "isun", "idra", "lmat", "jspr", "evgn", "prof", "ear", "apgn", "lmb", "brez", "casi", "tnc", "taop", "clir", "vmd", "ubfo", "bpth", "abgi", "ahrn", "lsta", "tsbk", "bsqr", "aeac", "pcb", "rgco", "dlpn", "atni", "ontx", "glre", "alrn", "invo", "rvsb", "bkyi", "flic", "ibcp", "hska", "pfbc", "gsbc", "cpsh", "bwen", "nrt", "gwrs", "alpa", "unty", "amv", "acnb", "rvlp", "fcbc", "pkbk", "lyra", "pmcb", "gamb", "onvo", "enlv", "cpss", "krmd", "kvhi", "ctg", "plus", "nmrd", "crmt", "rain", "kbnt", "osis", "pixy", "pwfl", "ffnw", "usau", "alot", "evtv", "hall", "fhtx", "lkfn", "rani", "prso", "fncb", "tcx", "ivac", "albt", "igic", "intz", "rbcaa", "grc", "biox", "fnlc", "pgc", "vcnx", "ganx", "fsv", "ciso", "crvl", "glto", "galt", "sasi", "nbrv", "imnm", "sypr", "cnty", "fcnca", "ssti", "ptmn", "zest", "anix", "alvo", "orrf", "nmtr", "msbi", "plur", "forr", "srce", "crws", "phio", "ckpt", "bwac", "tbnk", "hbt", "lvtx", "mkl", "chco", "wrld", "swvl", "ekso", "gtim", "plse", "ipa", "iron", "ltrn", "fora", "htbi", "fkwl", "smmf", "dlhc", "mndo", "acon", "dbtx", "clgn", "mcri", "vrca", "xbio", "inta", "prph", "aty", "lcut", "byrn", "thrn", "gmgi", "geos", "fbrx", "hffg", "ampg", "xoma", "aadi", "urgn", "conx", "celc", "usap", "qcrh", "bwb", "nhtc", "liqt", "incr", "trvn", "dooo", "mfin", "msex", "alya", "alor", "wldn", "frst", "pkoh", "gabc", "alps", "oss", "bbsi", "gifi", "fstr", "cvrx", "fusn", "fnch", "pke", "tbio", "sclx", "dco", "culp", "idn", "twin", "inmb", "ptpi", "loop", "bior", "amao", "lake", "wksp", "aib", "pesi", "altu", "afar", "zeus", "sttk", "myrg", "scwx", "cvcy", "adse", "hwkn", "dxpe", "jbss", "reun", "wsbf", "shbi", "huge", "mcbc", "pypd", "cmct", "yorw", "blfy", "smbk", "jakk", "care", "lfvn", "aeae", "aimd", "vvos", "srdx", "cent", "mbwm", "nvee", "sgc", "sanw", "atcx", "alrs", "envb", "ueic", "cstr", "tipt", "acah", "irmd", "pyr", "adoc", "rail", "dmac", "ap", "mgic", "iti", "cnxn", "issc", "prld", "achv", "tlsa", "rsvr", "evok", "samg", "acrv", "rdi", "vrar", "tact", "dcth", "mntx", "atlc", "omex", "necb", "lsak", "aei", "rbb", "ebf", "qipt", "prpo", "rfil", "pbpb", "usio", "opbk", "reax", "rmr", "bcpc", "arbe", "tela", "hoft", "sgtx", "belfb", "aeye", "slgl", "refr", "aaci", "esca", "adth", "atlo", "cigi", "aytu", "trst", "janx", "gnss", "ofs", "alsa", "fcuv", "sfe", "aihs", "inbk", "amnb", "rxst", "ipar", "lmnr", "cala", "lfcr", "alco", "qrhc", "mchx", "modv", "dxyn", "nvno", "aeha", "natr", "amtb", "cpix", "nuze", "cxdo", "dyai", "mbin", "hayn", "bttx", "clps", "alim", "ram", "fdus", "bsrr", "cplp", "enob", "rgls", "ctbi", "crai", "agm", "flnt", "airg", "cban", "upxi", "lcnb", "egan", "smbc", "ocg", "gaia", "daio", "rmti", "sevn", "trc", "banf", "cpsi", "lnsr", "evlo", "wdfc", "utl", "acba", "elox", "dave", "bfi", "ahpi", "lbc", "mgee");
        for (String s : stockList) {
            System.out.println(s + " " + allMap.get(s.toUpperCase()));
        }
    }
}
