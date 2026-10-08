/*
 * scp.h
 *
 *  Created on: 4 wrz 2017
 *      Author: Marcin Antczak
 */

#ifndef SCP_H_
#define SCP_H_


/*---------------------------------------------------------------*/
/* Values for SCP-ECG file configuration */
/*---------------------------------------------------------------*/

#define CALCULATE_ADDITIONAL_LEADS	0

#define SCP_MANUFACTURER_STRING		"Pro-PLUS"			// device manufacturer

#define SCP_6_Vx_CODE				SCP_6_V1_CODE		// wybrane odporwadzenie przedsercowe
#define SCP_6_III_CODE				SCP_6_dIII_CODE		// wybrany typ odprowadzenia III: r = zmierzone, d = wyliczone
#define SCP_6_aVR_CODE				SCP_6_daVR_CODE		// wybrany typ odprowadzenia aVR: r = zmierzone, d = wyliczone
#define SCP_6_aVL_CODE				SCP_6_daVL_CODE		// wybrany typ odprowadzenia aVL: r = zmierzone, d = wyliczone
#define SCP_6_aVF_CODE				SCP_6_daVF_CODE		// wybrany typ odprowadzenia aVF: r = zmierzone, d = wyliczone

#define SCP_1_SCP_VERSION			(20)				// version 2.0
#define SCP_1_COMPATIBILITY_LVL		(0x0D)				// lvl I (lvl II = 0x0E)
#define SCP_1_LANGUAGE_CODES		(0)					// ascii and Latin-1
#define SCP_1_DEVICE_CAPABILITY		((1<<7) | (1<<6))	// record and store data
#define SCP_1_POWER_FREQ			(1)					// 50Hz
#define SCP_1_ADDITIONAL_DATA_LEN	(1)					// empty string length
#define SCP_1_INST_NUMBER			(0)					// institution number
#define SCP_1_DEPART_NUMBER			(0)					// department number
#define SCP_1_DEVICE_ID				(0)					// device id
#define SCP_1_DEVICE_TYPE			(0)					// measuring device

#define SCP_3_COMPRESSION_ENABLED	(0)					// no compression
#define SCP_3_PARALLEL_RECORDING	(1)					// parallel data recording

#define SCP_6_AVM					(7100)
#define SCP_6_BIOMODUALCOMPRESSION	(0)
#define SCP_6_USEDDIFFERENCE		(0)


#define SCP_PROTOCOL_VERSION		20					//
#define SCP_SECTION_0_VERSION		10					//
#define SCP_SECTION_1_VERSION		10					//
#define SCP_SECTION_3_VERSION		10					//
#define SCP_SECTION_6_VERSION		10					//


/*---------------------------------------------------------------*/
/* Constants for SCP-ECG file format */
/*---------------------------------------------------------------*/

#define SCP_IDENTICATION_STRING		"SCPECG"			// do not change
#define SCP_NUMBER_OF_SECTIONS 		(12)  				// do not change
#define SCP_SECTION_0_ID			(0)					// do not change
#define SCP_SECTION_1_ID			(1)					// do not change
#define SCP_SECTION_3_ID			(3)					// do not change
#define SCP_SECTION_6_ID			(6)					// do not change

#define SCP_1_PATIENT_ID_TAG		(2)					// do not change
#define SCP_1_DEVICE_ID_TAG			(14)				// do not change
#define SCP_1_DATE_TAG				(25)				// do not change
#define SCP_1_TIME_TAG				(26)				// do not change
#define SCP_1_TERMINATOR_TAG		(255)				// do not change

#define SCP_1_DEPRACATED_VALUE		(255)				// do not change
#define SCP_1_DEVICE_ID_LEN			(41)				// do not change
#define SCP_1_DATE_LEN				(4)					// do not change
#define SCP_1_TIME_LEN				(3)					// do not change
#define SCP_1_TERMINATOR_LEN		(0)					// do not change

#define SCP_6_RL_CODE				(147)				// do not change
#define SCP_6_RA_CODE				(22)				// do not change
#define SCP_6_RM_CODE				(105)				// do not change
#define SCP_6_I_CODE				(1)					// do not change
#define SCP_6_II_CODE				(2)					// do not change
#define SCP_6_rIII_CODE				(61)				// do not change
#define SCP_6_raVR_CODE				(62)				// do not change
#define SCP_6_raVL_CODE				(63)				// do not change
#define SCP_6_raVF_CODE				(64)				// do not change
#define SCP_6_dIII_CODE				(111)				// do not change
#define SCP_6_daVR_CODE				(112)				// do not change
#define SCP_6_daVL_CODE				(113)				// do not change
#define SCP_6_daVF_CODE				(114)				// do not change
#define SCP_6_V1_CODE				(3)					// do not change
#define SCP_6_V2_CODE				(4)					// do not change
#define SCP_6_V3_CODE				(5)					// do not change
#define SCP_6_V4_CODE				(6)					// do not change
#define SCP_6_V5_CODE				(7)					// do not change
#define SCP_6_V6_CODE				(8)					// do not change
#define SCP_6_V7_CODE				(9)					// do not change

#define SECTION_0_POSITION			(6)					// do not change
#define SECTION_1_POSITION			(SECTION_0_POSITION + sizeof(SCP_SECTION_0_TYPE)) 	// do not change


typedef enum
{
	SCP_LEAD_I,
	SCP_LEAD_II,
#if (CALCULATE_ADDITIONAL_LEADS == 1)
	SCP_LEAD_III,
	SCP_LEAD_aVL,
	SCP_LEAD_aVR,
	SCP_LEAD_aVF,
#endif
	SCP_LEAD_Vx,
	SCP_LEADS_TOTAL
} eScpLead;



/*---------------------------------------------------------------*/
/* Section header */
/*---------------------------------------------------------------*/

/* 5.27 */
typedef struct
{
    u16 crc;                /*1-2*/
    u16 section_id;         /*3-4*/
    u32 len;                /*5-8*/
    u8 section_version;     /*9*/
    u8 protocol_version;    /*10*/
    u8 reserved[6];         /*11 - 16*/
} __attribute__((packed)) SCP_HDR_TYPE;


/*---------------------------------------------------------------*/
/* Section 0 */
/*---------------------------------------------------------------*/

typedef struct
{
    u16 section_id;  		/* numer sekcji od 0 do 11 */
    u32 section_len_even; 	/* rozmiar 0 gdy sekcja nie uzywana */
    u32 section_prt;		/* numer bajtu w pliku ktory ropozczyna sekcje */
} __attribute__((packed)) SCP_PTR_SECTION_TYPE; //5.3.4

typedef struct
{
    SCP_HDR_TYPE hdr; /* Reserved zawiera wartosc SCPECG 5.3.2 */
    SCP_PTR_SECTION_TYPE secion[SCP_NUMBER_OF_SECTIONS]; //5.3.3
} __attribute__((packed)) SCP_SECTION_0_TYPE; //5.3.4


/*---------------------------------------------------------------*/
/* Section 1 */
/*---------------------------------------------------------------*/

typedef struct
{
	u8  patient_ID_tag;
	u16 patient_ID_len;
	u8* patient_ID_str;
} __attribute__((packed)) SCP_SECTION_1_PATIENT_ID_TYPE;

typedef struct
{
	u8  device_ID_tag;
	u16 device_ID_len;
    u16 institution_nr;  	/*1-2*/
    u16 department_nr; 		/*3-4*/
    u16 device_nr;			/*5-6*/
    u8  device_type; 		/*7*/
    u8  deprecated;			/*8*/
    u8	device_name[6];		/*9-14*/
    u8	scp_version;		/*15*/
    u8  compatibility_lvl;	/*16*/
    u8	language_codes;		/*17*/
    u8  device_capability;	/*18*/
    u8  power_freq;			/*19*/
    u8  reserved[16];		/*20-35*/
    u8  additional_len;		/*36*/
    u8  additional_data;	/*37*/
} __attribute__((packed)) SCP_SECTION_1_DEVICE_ID_TYPE;

typedef struct
{
    u8  date_tag;
    u16 date_len;
    u16 date_year;
    u8  date_month;
    u8  date_day;
} __attribute__((packed)) SCP_SECTION_1_DATE_TYPE;

typedef struct
{
    u8  time_tag;
    u16 time_len;
    u8  time_hour;
    u8  time_minute;
    u8  time_second;
} __attribute__((packed)) SCP_SECTION_1_TIME_TYPE;

typedef struct
{
	u8  terminator_tag;
	u16 terminator_len;
} __attribute__((packed)) SCP_SECTION_1_TERMIANTOR_TYPE;

typedef struct
{
    SCP_HDR_TYPE hdr;
    SCP_SECTION_1_PATIENT_ID_TYPE patient_id;
    SCP_SECTION_1_DEVICE_ID_TYPE device_id;
    SCP_SECTION_1_DATE_TYPE date;
    SCP_SECTION_1_TIME_TYPE time;
    SCP_SECTION_1_TERMIANTOR_TYPE terminator;
} __attribute__((packed)) SCP_SECTION_1_TYPE;


/*---------------------------------------------------------------*/
/* Section 3 */
/*---------------------------------------------------------------*/

typedef union
{
	u8  R;
	struct
	{
		u8 compression 		: 1;
		u8 reserved 		: 1;
		u8 parallel_data	: 1;
		u8 parallel_leads   : 5;
	}__attribute__((packed));
} __attribute__((packed)) SCP_SECTION_3_FLAGS_TYPE;

typedef struct
{
	u32  begin_sample_num;
	u32  end_sample_num;
	u8	 lead_id;
} __attribute__((packed)) SCP_SECTION_3_LEAD_INFO_TYPE;

typedef struct
{
    SCP_HDR_TYPE hdr;
    u8 leads_count;
    SCP_SECTION_3_FLAGS_TYPE flags;
    SCP_SECTION_3_LEAD_INFO_TYPE lead[SCP_LEADS_TOTAL];
    u8 fill;
} __attribute__((packed)) SCP_SECTION_3_TYPE;


/*---------------------------------------------------------------*/
/* Section 6 */
/*---------------------------------------------------------------*/
typedef struct
{
	u16  AVM;                   //AVM np 1250 -> 1,250*10e-6
	u16  sample_period;         //1000000/sampleRate
	u8   used_difference;       //0 rzeczywiste dane uzyte do zapisu,
								//1 pierwsze roznice danych uzyte do zapisu rytmu,
								//2 drugie uzyte roznice danych uzyte do zapisu rytmu
	u8 biomodualcompression; 	//0 - not used //1 - used
} __attribute__((packed)) SCP_SECTION_6_HDR_TYPE; //5.9.3

typedef struct
{
	SCP_HDR_TYPE hdr;                    //5.9.2
	SCP_SECTION_6_HDR_TYPE section_hdr;  //5.9.3
	u16 lead_len[SCP_LEADS_TOTAL];       //5.9.3  Wektor dlugosci odprowadzen w bajtach [len_lead1][len_lead[2][len_lead[3]
	u16 *lead_data;            //5.9.5 array of lead data [data_lead1][data_lead[2][data_lead[3]
							   //Odprowadzenia s¹ kodowane w kolejnoœci podanej w Sekcji 3 Jeœli nie ma Sekcji 2,
							   //to danym EKG (odejmowane albo nieodejmowane) nale¿y
						   //nadawaæ format dwubajtowych liczb ca³kowitych ze znakiem
} __attribute__((packed)) SCP_SECTION_6_TYPE;//5.9.6
/*---------------------------------------------------------------*/

typedef struct
{
    u16 crc; /*5.25 rysunek 1 - calego pliku bez tego pola 5.2.4*/
    u32 len; /*5.25 rysunek 1 - dlugosc calego pliku lacznie z crc 5.2.4 */

   /* Tablica 1 - wszystkie wymagane sekcje 5.2.11*/
    SCP_SECTION_0_TYPE section_0;
    SCP_SECTION_1_TYPE section_1;
    SCP_SECTION_3_TYPE section_3;
    SCP_SECTION_6_TYPE section_6;
} SCP_FILE_TYPE;



s32 create_ecg_scp_file(s32 n_ecgmsg);

#endif /* CUSTOM_SCP_H_ */
