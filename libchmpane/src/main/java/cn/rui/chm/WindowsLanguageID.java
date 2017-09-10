/**
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com) All Right Reserved
 * File     : WindowsLanguageID.java
 * Created	: 2007-3-1
 * 
 * ****************************************************************************
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com)
 * http://chmpane.sourceforge.net, All Right Reserved. 
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "CHMPane" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * *****************************************************************************
 */
package cn.rui.chm;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WindowsLanguageID {
	
	private static final Map<Integer, Locale> lcidToLocale = loadMap();

	/**
	 * @see <a href='https://msdn.microsoft.com/en-us/library/cc233982'>Windows Language Code Identifier (LCID) list</a>
	 * @return map of Windows LCID to Java Locale
	 */
	private static Map<Integer, Locale> loadMap() {
		Map<Integer, Locale> map = new HashMap<Integer, Locale>();
		map.put(0x0436, new Locale("af", "ZA", "")); // Afrikaans
		map.put(0x041c, new Locale("sq", "AL", "")); // Albanian
		map.put(0x0401, new Locale("ar", "SA", "")); // Arabic - Saudi Arabia
		map.put(0x0801, new Locale("ar", "IQ", "")); // Arabic - Iraq
		map.put(0x0c01, new Locale("ar", "EG", "")); // Arabic - Egypt
		map.put(0x1001, new Locale("ar", "LY", "")); // Arabic - Libya
		map.put(0x1401, new Locale("ar", "DZ", "")); // Arabic - Algeria
		map.put(0x1801, new Locale("ar", "MA", "")); // Arabic - Morocco
		map.put(0x1c01, new Locale("ar", "TN", "")); // Arabic - Tunisia
		map.put(0x2001, new Locale("ar", "OM", "")); // Arabic - Oman
		map.put(0x2401, new Locale("ar", "YE", "")); // Arabic - Yemen
		map.put(0x2801, new Locale("ar", "SY", "")); // Arabic - Syria
		map.put(0x2c01, new Locale("ar", "JO", "")); // Arabic - Jordan
		map.put(0x3001, new Locale("ar", "LB", "")); // Arabic - Lebanon
		map.put(0x3401, new Locale("ar", "KW", "")); // Arabic - Kuwait
		map.put(0x3801, new Locale("ar", "AE", "")); // Arabic - United Arab Emirates
		map.put(0x3c01, new Locale("ar", "BH", "")); // Arabic - Bahrain
		map.put(0x4001, new Locale("ar", "QA", "")); // Arabic - Qatar
		map.put(0x042b, new Locale("hy", "AM", "")); // Armenian
		map.put(0x042c, new Locale("az", "AZ", "")); // Azeri Latin
		map.put(0x082c, new Locale("az", "AZ", "")); // Azeri - Cyrillic
		map.put(0x042d, new Locale("eu", "ES", "")); // Basque
		map.put(0x0423, new Locale("be", "BY", "")); // Belarusian
		map.put(0x0445, new Locale("bn", "IN", "")); // Begali
		map.put(0x201a, new Locale("bs", "BA", "")); // Bosnian
		map.put(0x141a, new Locale("bs", "BA", "")); // Bosnian - Cyrillic
		map.put(0x047e, new Locale("br", "FR", "")); // Breton - France
		map.put(0x0402, new Locale("bg", "BG", "")); // Bulgarian
		map.put(0x0403, new Locale("ca", "ES", "")); // Catalan
		map.put(0x0004, new Locale("zh", "CHS", "")); // Chinese - Simplified
		map.put(0x0404, new Locale("zh", "TW", "")); // Chinese - Taiwan
		map.put(0x0804, new Locale("zh", "CN", "")); // Chinese - PRC
		map.put(0x0c04, new Locale("zh", "HK", "")); // Chinese - Hong Kong S.A.R.
		map.put(0x1004, new Locale("zh", "SG", "")); // Chinese - Singapore
		map.put(0x1404, new Locale("zh", "MO", "")); // Chinese - Macao S.A.R.
		map.put(0x7c04, new Locale("zh", "CHT", "")); // Chinese - Traditional
		map.put(0x041a, new Locale("hr", "HR", "")); // Croatian
		map.put(0x101a, new Locale("hr", "BA", "")); // Croatian - Bosnia
		map.put(0x0405, new Locale("cs", "CZ", "")); // Czech
		map.put(0x0406, new Locale("da", "DK", "")); // Danish
		map.put(0x048c, new Locale("gbz", "AF", "")); // Dari - Afghanistan
		map.put(0x0465, new Locale("div", "MV", "")); // Divehi - Maldives
		map.put(0x0413, new Locale("nl", "NL", "")); // Dutch - The Netherlands
		map.put(0x0813, new Locale("nl", "BE", "")); // Dutch - Belgium
		map.put(0x0409, new Locale("en", "US", "")); // English - United States
		map.put(0x0809, new Locale("en", "GB", "")); // English - United Kingdom
		map.put(0x0c09, new Locale("en", "AU", "")); // English - Australia
		map.put(0x1009, new Locale("en", "CA", "")); // English - Canada
		map.put(0x1409, new Locale("en", "NZ", "")); // English - New Zealand
		map.put(0x1809, new Locale("en", "IE", "")); // English - Ireland
		map.put(0x1c09, new Locale("en", "ZA", "")); // English - South Africa
		map.put(0x2009, new Locale("en", "JA", "")); // English - Jamaica
		map.put(0x2409, new Locale("en", "CB", "")); // English - Carribbean
		map.put(0x2809, new Locale("en", "BZ", "")); // English - Belize
		map.put(0x2c09, new Locale("en", "TT", "")); // English - Trinidad
		map.put(0x3009, new Locale("en", "ZW", "")); // English - Zimbabwe
		map.put(0x3409, new Locale("en", "PH", "")); // English - Phillippines
		map.put(0x0425, new Locale("et", "EE", "")); // Estonian
		map.put(0x0438, new Locale("fo", "FO", "")); // Faroese
		map.put(0x0464, new Locale("fil", "PH", "")); // Filipino
		map.put(0x040b, new Locale("fi", "FI", "")); // Finnish
		map.put(0x040c, new Locale("fr", "FR", "")); // French - France
		map.put(0x080c, new Locale("fr", "BE", "")); // French - Belgium
		map.put(0x0c0c, new Locale("fr", "CA", "")); // French - Canada
		map.put(0x100c, new Locale("fr", "CH", "")); // French - Switzerland
		map.put(0x140c, new Locale("fr", "LU", "")); // French - Luxembourg
		map.put(0x180c, new Locale("fr", "MC", "")); // French - Monaco
		map.put(0x0462, new Locale("fy", "NL", "")); // Frisian - Netherlands
		map.put(0x0456, new Locale("gl", "ES", "")); // Galician
		map.put(0x0437, new Locale("ka", "GE", "")); // Georgian
		map.put(0x0407, new Locale("de", "DE", "")); // German - Germany
		map.put(0x0807, new Locale("de", "CH", "")); // German - Switzerland
		map.put(0x0c07, new Locale("de", "AT", "")); // German - Austria
		map.put(0x1007, new Locale("de", "LU", "")); // German - Luxembourg
		map.put(0x1407, new Locale("de", "LI", "")); // German - Liechtenstein
		map.put(0x0408, new Locale("el", "GR", "")); // Greek
		map.put(0x0447, new Locale("gu", "IN", "")); // Gujarati
		map.put(0x040d, new Locale("he", "IL", "")); // Hebrew
		map.put(0x0439, new Locale("hi", "IN", "")); // Hindi
		map.put(0x040e, new Locale("hu", "HU", "")); // Hungarian
		map.put(0x040f, new Locale("is", "IS", "")); // Icelandic
		map.put(0x0421, new Locale("id", "ID", "")); // Indonesian
		map.put(0x045d, new Locale("iu", "CA", "")); // Inuktitut
		map.put(0x085d, new Locale("iu", "CA", "")); // Inuktitut - Latin
		map.put(0x083c, new Locale("ga", "IE", "")); // Irish - Ireland
		map.put(0x0410, new Locale("it", "IT", "")); // Italian - Italy
		map.put(0x0810, new Locale("it", "CH", "")); // Italian - Switzerland
		map.put(0x0411, new Locale("ja", "JP", "")); // Japanese
		map.put(0x044b, new Locale("kn", "IN", "")); // Kannada - India
		map.put(0x043f, new Locale("kk", "KZ", "")); // Kazakh
		map.put(0x0457, new Locale("kok", "IN", "")); // Konkani
		map.put(0x0412, new Locale("ko", "KR", "")); // Korean
		map.put(0x0440, new Locale("ky", "KG", "")); // Kyrgyz
		map.put(0x0426, new Locale("lv", "LV", "")); // Latvian
		map.put(0x0427, new Locale("lt", "LT", "")); // Lithuanian
		map.put(0x046e, new Locale("lb", "LU", "")); // Luxembourgish
		map.put(0x042f, new Locale("mk", "MK", "")); // FYRO Macedonian
		map.put(0x043e, new Locale("ms", "MY", "")); // Malay - Malaysia
		map.put(0x083e, new Locale("ms", "BN", "")); // Malay - Brunei
		map.put(0x044c, new Locale("ml", "IN", "")); // Malayalam - India
		map.put(0x043a, new Locale("mt", "MT", "")); // Maltese
		map.put(0x0481, new Locale("mi", "NZ", "")); // Maori
		map.put(0x047a, new Locale("arn", "CL", "")); // Mapudungun
		map.put(0x044e, new Locale("mr", "IN", "")); // Marathi
		map.put(0x047c, new Locale("moh", "CA", "")); // Mohawk - Canada
		map.put(0x0450, new Locale("mn", "MN", "")); // Mongolian
		map.put(0x0461, new Locale("ne", "NP", "")); // Nepali
		map.put(0x0414, new Locale("nb", "NO", "")); // Norwegian - Bokmal
		map.put(0x0814, new Locale("nn", "NO", "")); // Norwegian - Nynorsk
		map.put(0x0482, new Locale("oc", "FR", "")); // Occitan - France
		map.put(0x0448, new Locale("or", "IN", "")); // Oriya - India
		map.put(0x0463, new Locale("ps", "AF", "")); // Pashto - Afghanistan
		map.put(0x0429, new Locale("fa", "IR", "")); // Persian
		map.put(0x0415, new Locale("pl", "PL", "")); // Polish
		map.put(0x0416, new Locale("pt", "BR", "")); // Portuguese - Brazil
		map.put(0x0816, new Locale("pt", "PT", "")); // Portuguese - Portugal
		map.put(0x0446, new Locale("pa", "IN", "")); // Punjabi
		map.put(0x046b, new Locale("quz", "BO", "")); // Quechua - Bolivia
		map.put(0x086b, new Locale("quz", "EC", "")); // Quechua - Ecuador
		map.put(0x0c6b, new Locale("quz", "PE", "")); // Quechua - Peru
		map.put(0x0418, new Locale("ro", "RO", "")); // Romanian - Romania
		map.put(0x0417, new Locale("rm", "CH", "")); // Raeto-Romanese
		map.put(0x0419, new Locale("ru", "RU", "")); // Russian
		map.put(0x243b, new Locale("smn", "FI", "")); // Sami Finland
		map.put(0x103b, new Locale("smj", "NO", "")); // Sami Norway
		map.put(0x143b, new Locale("smj", "SE", "")); // Sami Sweden
		map.put(0x043b, new Locale("se", "NO", "")); // Sami Northern Norway
		map.put(0x083b, new Locale("se", "SE", "")); // Sami Northern Sweden
		map.put(0x0c3b, new Locale("se", "FI", "")); // Sami Northern Finland
		map.put(0x203b, new Locale("sms", "FI", "")); // Sami Skolt
		map.put(0x183b, new Locale("sma", "NO", "")); // Sami Southern Norway
		map.put(0x1c3b, new Locale("sma", "SE", "")); // Sami Southern Sweden
		map.put(0x044f, new Locale("sa", "IN", "")); // Sanskrit
		map.put(0x0c1a, new Locale("sr", "SP", "")); // Serbian - Cyrillic
		map.put(0x1c1a, new Locale("sr", "BA", "")); // Serbian - Bosnia Cyrillic
		map.put(0x081a, new Locale("sr", "SP", "")); // Serbian - Latin
		map.put(0x181a, new Locale("sr", "BA", "")); // Serbian - Bosnia Latin
		map.put(0x046c, new Locale("ns", "ZA", "")); // Northern Sotho
		map.put(0x0432, new Locale("tn", "ZA", "")); // Setswana - Southern Africa
		map.put(0x041b, new Locale("sk", "SK", "")); // Slovak
		map.put(0x0424, new Locale("sl", "SI", "")); // Slovenian
		map.put(0x040a, new Locale("es", "ES", "")); // Spanish - Spain
		map.put(0x080a, new Locale("es", "MX", "")); // Spanish - Mexico
		map.put(0x0c0a, new Locale("es", "ES", "")); // Spanish - Spain (Modern)
		map.put(0x100a, new Locale("es", "GT", "")); // Spanish - Guatemala
		map.put(0x140a, new Locale("es", "CR", "")); // Spanish - Costa Rica
		map.put(0x180a, new Locale("es", "PA", "")); // Spanish - Panama
		map.put(0x1c0a, new Locale("es", "DO", "")); // Spanish - Dominican Republic
		map.put(0x200a, new Locale("es", "VE", "")); // Spanish - Venezuela
		map.put(0x240a, new Locale("es", "CO", "")); // Spanish - Colombia
		map.put(0x280a, new Locale("es", "PE", "")); // Spanish - Peru
		map.put(0x2c0a, new Locale("es", "AR", "")); // Spanish - Argentina
		map.put(0x300a, new Locale("es", "EC", "")); // Spanish - Ecuador
		map.put(0x340a, new Locale("es", "CL", "")); // Spanish - Chile
		map.put(0x380a, new Locale("es", "UR", "")); // Spanish - Uruguay
		map.put(0x3c0a, new Locale("es", "PY", "")); // Spanish - Paraguay
		map.put(0x400a, new Locale("es", "BO", "")); // Spanish - Bolivia
		map.put(0x440a, new Locale("es", "SV", "")); // Spanish - El Salvador
		map.put(0x480a, new Locale("es", "HN", "")); // Spanish - Honduras
		map.put(0x4c0a, new Locale("es", "NI", "")); // Spanish - Nicaragua
		map.put(0x500a, new Locale("es", "PR", "")); // Spanish - Puerto Rico
		map.put(0x0441, new Locale("sw", "KE", "")); // Swahili
		map.put(0x041d, new Locale("sv", "SE", "")); // Swedish - Sweden
		map.put(0x081d, new Locale("sv", "FI", "")); // Swedish - Finland
		map.put(0x045a, new Locale("syr", "SY", "")); // Syriac
		map.put(0x0449, new Locale("ta", "IN", "")); // Tamil
		map.put(0x0444, new Locale("tt", "RU", "")); // Tatar
		map.put(0x044a, new Locale("te", "IN", "")); // Telugu
		map.put(0x041e, new Locale("th", "TH", "")); // Thai
		map.put(0x041f, new Locale("tr", "TR", "")); // Turkish
		map.put(0x0422, new Locale("uk", "UA", "")); // Ukrainian
		map.put(0x0420, new Locale("ur", "PK", "")); // Urdu
		map.put(0x0820, new Locale("ur", "IN", "")); // Urdu - India
		map.put(0x0443, new Locale("uz", "UZ", "")); // Uzbek - Latin
		map.put(0x0843, new Locale("uz", "UZ", "")); // Uzbek - Cyrillic
		map.put(0x042a, new Locale("vi", "VN", "")); // Vietnamese
		map.put(0x0452, new Locale("cy", "GB", "")); // Welsh
		map.put(0x0434, new Locale("xh", "ZA", "")); // Xhosa - South Africa
		map.put(0x0435, new Locale("zu", "ZA", "")); // Zulu
		return map;
	}
	
	public static Locale getLocale(int lcid) {
		return lcidToLocale.get(lcid);
	}


	/**
	 * Code page that marked as "Unicode only"
	 * @see <a href='https://msdn.microsoft.com/en-us/library/aa913244'>Code Pages</a>
	 */
	private static final int UCCP = 1200; // Unicode UTF-16, little endian

	private static final Map<Integer, Integer> lcidToCodePage = loadLcidToCodePage();

	/**
	 * @see <a href='https://msdn.microsoft.com/en-us/library/aa912040'>Default code page of Language identifier</a>
	 * @return map of Windows LCID to Windows default code page
	 */
	private static Map<Integer, Integer> loadLcidToCodePage() {
		Map<Integer, Integer> map = new HashMap<Integer, Integer>();
		map.put(0x0436, 1252); // AFK , Afrikaans - South Africa
		map.put(0x041c, 1250); // SQI , Albanian - Albania
		map.put(0x1401, 1256); // ARG , Arabic - Algeria
		map.put(0x3c01, 1256); // ARH , Arabic - Bahrain
		map.put(0x0c01, 1256); // ARE , Arabic - Egypt
		map.put(0x0801, 1256); // ARI , Arabic - Iraq
		map.put(0x2c01, 1256); // ARJ , Arabic - Jordan
		map.put(0x3401, 1256); // ARK , Arabic - Kuwait
		map.put(0x3001, 1256); // ARB , Arabic - Lebanon
		map.put(0x1001, 1256); // ARL , Arabic - Libya
		map.put(0x1801, 1256); // ARM , Arabic - Morocco
		map.put(0x2001, 1256); // ARO , Arabic - Oman
		map.put(0x4001, 1256); // ARQ , Arabic - Qatar
		map.put(0x0401, 1256); // ARA , Arabic - Saudi Arabia
		map.put(0x2801, 1256); // ARS , Arabic - Syria
		map.put(0x1c01, 1256); // ART , Arabic - Tunisia
		map.put(0x3801, 1256); // ARU , Arabic - U.A.E.
		map.put(0x2401, 1256); // ARY , Arabic - Yemen
		map.put(0x042b, UCCP); // HYE , Armenian - Armenia
		map.put(0x082c, 1251); // AZE , Azeri - Azerbaijan (Cyrillic)
		map.put(0x042c, 1254); // AZE , Azeri - Azerbaijan (Latin)
		map.put(0x042d, 1252); // EUQ , Basque - Spain
		map.put(0x0423, 1251); // BEL , Belarusian - Belarus
		map.put(0x0402, 1251); // BGR , Bulgarian - Bulgaria
		map.put(0x0403, 1252); // CAT , Catalan - Spain
		map.put(0x0c04, 950); // ZHH , Chinese - Hong Kong SAR
		map.put(0x1404, 950); // ZHM , Chinese - Macao SAR
		map.put(0x0804, 936); // CHS , Chinese - PRC
		map.put(0x1004, 936); // ZHI , Chinese - Singapore
		map.put(0x0404, 950); // CHT , Chinese - Taiwan
		map.put(0x041a, 1250); // HRV , Croatian - Croatia
		map.put(0x0405, 1250); // CSY , Czech - Czech Republic
		map.put(0x0406, 1252); // DAN , Danish - Denmark
		map.put(0x0465, UCCP); // DIV , Divehi - Maldives
		map.put(0x0813, 1252); // NLB , Dutch - Belgium
		map.put(0x0413, 1252); // NLD , Dutch - Netherlands
		map.put(0x0c09, 1252); // ENA , English - Australia
		map.put(0x2809, 1252); // ENL , English - Belize
		map.put(0x1009, 1252); // ENC , English - Canada
		map.put(0x2409, 1252); // ENB , English - Caribbean
		map.put(0x1809, 1252); // ENI , English - Ireland
		map.put(0x2009, 1252); // ENJ , English - Jamaica
		map.put(0x1409, 1252); // ENZ , English - New Zealand
		map.put(0x3409, 1252); // ENP , English - Philippines
		map.put(0x1c09, 1252); // ENS , English - South Africa
		map.put(0x2c09, 1252); // ENT , English - Trinidad
		map.put(0x0809, 1252); // ENG , English - United Kingdom
		map.put(0x0409, 1252); // USA , English - United States
		map.put(0x3009, 1252); // ENW , English - Zimbabwe
		map.put(0x0425, 1257); // ETI , Estonian - Estonia
		map.put(0x0438, 1252); // FOS , Faroese - Faroe Islands
		map.put(0x0429, 1256); // FAR , Farsi - Iran
		map.put(0x040b, 1252); // FIN , Finnish - Finland
		map.put(0x080c, 1252); // FRB , French - Belgium
		map.put(0x0c0c, 1252); // FRC , French - Canada
		map.put(0x040c, 1252); // FRA , French - France
		map.put(0x140c, 1252); // FRL , French - Luxembourg
		map.put(0x180c, 1252); // FRM , French - Monaco
		map.put(0x100c, 1252); // FRS , French - Switzerland
		map.put(0x042f, 1251); // MKI , F.Y.R.O. Macedonia - F.Y.R.O. Macedonia
		map.put(0x0456, 1252); // GLC , Galician - Spain
		map.put(0x0437, UCCP); // KAT , Georgian - Georgia
		map.put(0x0c07, 1252); // DEA , German - Austria
		map.put(0x0407, 1252); // DEU , German - Germany
		map.put(0x1407, 1252); // DEC , German - Liechtenstein
		map.put(0x1007, 1252); // DEL , German - Luxembourg
		map.put(0x0807, 1252); // DES , German - Switzerland
		map.put(0x0408, 1253); // ELL , Greek - Greece
		map.put(0x0447, UCCP); // GUJ , Gujarati - India
		map.put(0x040d, 1255); // HEB , Hebrew - Israel
		map.put(0x0439, UCCP); // HIN , Hindi - India
		map.put(0x040e, 1250); // HUN , Hungarian - Hungary
		map.put(0x040f, 1252); // ISL , Icelandic - Iceland
		map.put(0x0421, 1252); // IND , Indonesian - Indonesia (Bahasa)
		map.put(0x0410, 1252); // ITA , Italian - Italy
		map.put(0x0810, 1252); // ITS , Italian - Switzerland
		map.put(0x0411, 932); // JPN , Japanese - Japan
		map.put(0x044b, UCCP); // KAN , Kannada - India (Kannada script)
		map.put(0x043f, 1251); // KKZ , Kazakh - Kazakstan
		map.put(0x0457, UCCP); // KNK , Konkani - India
		map.put(0x0412, 949); // KOR , Korean - Korea
		map.put(0x0440, 1251); // KYR , Kyrgyz - Kyrgyzstan
		map.put(0x0426, 1257); // LVI , Latvian - Latvia
		map.put(0x0427, 1257); // LTH , Lithuanian - Lithuania
		map.put(0x083e, 1252); // MSB , Malay - Brunei Darussalam
		map.put(0x043e, 1252); // MSL , Malay - Malaysia
		map.put(0x044e, UCCP); // MAR , Marathi - India
		map.put(0x0450, 1251); // MON , Mongolian (Cyrillic) - Mongolia
		map.put(0x0414, 1252); // NOR , Norwegian - Norway (Bokmål)
		map.put(0x0814, 1252); // NON , Norwegian - Norway (Nynorsk)
		map.put(0x0415, 1250); // PLK , Polish - Poland
		map.put(0x0416, 1252); // PTB , Portuguese - Brazil
		map.put(0x0816, 1252); // PTG , Portuguese - Portugal
		map.put(0x0446, UCCP); // PAN , Punjabi - India (Gurmukhi script)
		map.put(0x0418, 1250); // ROM , Romanian - Romania
		map.put(0x0419, 1251); // RUS , Russian - Russia
		map.put(0x044f, UCCP); // SAN , Sanskrit - India
		map.put(0x0c1a, 1251); // SRB , Serbian - Serbia (Cyrillic)
		map.put(0x081a, 1250); // SRL , Serbian - Serbia (Latin)
		map.put(0x041b, 1250); // SKY , Slovak - Slovakia
		map.put(0x0424, 1250); // SLV , Slovenian - Slovenia
		map.put(0x2c0a, 1252); // ESS , Spanish - Argentina
		map.put(0x400a, 1252); // ESB , Spanish - Bolivia
		map.put(0x340a, 1252); // ESL , Spanish - Chile
		map.put(0x240a, 1252); // ESO , Spanish - Colombia
		map.put(0x140a, 1252); // ESC , Spanish - Costa Rica
		map.put(0x1c0a, 1252); // ESD , Spanish - Dominican Republic
		map.put(0x300a, 1252); // ESF , Spanish - Ecuador
		map.put(0x440a, 1252); // ESE , Spanish - El Salvador
		map.put(0x100a, 1252); // ESG , Spanish - Guatemala
		map.put(0x480a, 1252); // ESH , Spanish - Honduras
		map.put(0x080a, 1252); // ESM , Spanish - Mexico
		map.put(0x4c0a, 1252); // ESI , Spanish - Nicaragua
		map.put(0x180a, 1252); // ESA , Spanish - Panama
		map.put(0x3c0a, 1252); // ESZ , Spanish - Paraguay
		map.put(0x280a, 1252); // ESR , Spanish - Peru
		map.put(0x500a, 1252); // ESU , Spanish - Puerto Rico
		map.put(0x040a, 1252); // ESP , Spanish - Spain (Traditional sort)
		map.put(0x0c0a, 1252); // ESN , Spanish - Spain (International sort)
		map.put(0x380a, 1252); // ESY , Spanish - Uruguay
		map.put(0x200a, 1252); // ESV , Spanish - Venezuela
		map.put(0x0441, 1252); // SWK , Swahili - Kenya
		map.put(0x081d, 1252); // SVF , Swedish - Finland
		map.put(0x041d, 1252); // SVE , Swedish - Sweden
		map.put(0x045a, UCCP); // SYR , Syriac - Syria
		map.put(0x0449, UCCP); // TAM , Tamil - India
		map.put(0x0444, 1251); // TTT , Tatar - Tatarstan
		map.put(0x044a, UCCP); // TEL , Telugu - India (Telugu script)
		map.put(0x041e, 874); // THA , Thai - Thailand
		map.put(0x041f, 1254); // TRK , Turkish - Turkey
		map.put(0x0422, 1251); // UKR , Ukrainian - Ukraine
		map.put(0x0420, 1256); // URP , Urdu - Pakistan
		map.put(0x0843, 1251); // UZB , Uzbek - Uzbekistan (Cyrillic)
		map.put(0x0443, 1254); // UZB , Uzbek - Uzbekistan (Latin)
		map.put(0x042a, 1258); // VIT , Vietnamese - Viet Nam
		return map;
	}

	public static Integer getCodePage(int lcid) {
		return lcidToCodePage.get(lcid);
	}

	private static final Map<Integer, String> codePageToCharset = loadCodePageToCharset();

	/**
	 * @see <a href='https://msdn.microsoft.com/en-us/library/windows/desktop/dd317756'>Windows Code Page Identifiers</a>
	 * @see <a href='https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html'>Java Charset for Supported Encodings</a>
	 * @return map of Windows code page to Java Charset name
	 */
	private static Map<Integer, String> loadCodePageToCharset() {
		Map<Integer, String> map = new HashMap<Integer, String>();

		map.put(1250, "cp1250"); // Windows Central European
		map.put(1251, "cp1251"); // Windows Cyrillic
		map.put(1252, "cp1252"); // Windows Western European , ANSI Latin-1 , often mislabeled as ISO-8859-1
		map.put(1253, "cp1253"); // Windows Greek
		map.put(1254, "cp1254"); // Windows Turkish
		map.put(1255, "cp1255"); // Windows Hebrew
		map.put(1256, "cp1256"); // Windows Arabic
		map.put(1257, "cp1257"); // Windows Baltic
		map.put(1258, "cp1258"); // Windows Vietnamese

		map.put(932, "Shift_JIS"); // ms932 , Windows Japanese
		map.put(936, "GBK"); // ms936 , Windows Simplified Chinese
		map.put(949, "ms949"); // Windows Korean
		map.put(950, "Big5"); // ms950 , Windows Traditional Chinese
		map.put(54936, "GB18030"); // GB18030 Simplified Chinese (4 byte)

		map.put(874, "ms874"); // Windows Thai

		// TODO complete the list

		// should we consider BOM ?
		//map.put(65000, "utf-7"); // no such charset in java
		map.put(65001, "utf-8");
		map.put(1200, "utf-16le");
		map.put(1201, "utf-16be");
		map.put(12000, "utf-32le");
		map.put(12001, "utf-32be");

		return map;
	}

	public static Charset getCharsetByCodePage(Integer codePage) {
		String charsetName = codePageToCharset.get(codePage);
		if (charsetName == null) {
			return null;
		}
		return Charset.forName(charsetName);
	}

	public static Charset getDefaultCharset(int lcid) {
		return getCharsetByCodePage(getCodePage(lcid));
	}

	public static void main(String[] args) {
		System.out.println(String.format("lcid count = %d", lcidToLocale.size()));
		System.out.println(String.format("known lcid count = %d", lcidToCodePage.size()));
		System.out.println(String.format("codepage count = %d", codePageToCharset.size()));
		for (Map.Entry<Integer, Integer> entry : lcidToCodePage.entrySet()) {
			int lcid = entry.getKey();
			int codePage = entry.getValue();
			Locale locale = getLocale(lcid);
			Charset charset = getCharsetByCodePage(codePage);
			System.out.println(String.format("lcid=%04X codepage=%4d locale=%s charset=%s", lcid, codePage, locale, charset));
		}

		System.out.println();
		System.out.println(String.format("AvailableLocales count = %d", Locale.getAvailableLocales().length));
		int matched = 0;
		for (Locale locale : Locale.getAvailableLocales()) {
			System.out.println(String.format("AvailableLocale %d %s", lcidToLocale.containsValue(locale)?1:0, locale));
			matched += lcidToLocale.containsValue(locale)?1:0;
		}
		System.out.println(String.format("matched locale count = %d", matched));

		System.out.println("charset for 0 " + getDefaultCharset(0));
	}
}
