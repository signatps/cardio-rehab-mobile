/*
 * bt_task_protocol.c
 *
 *  Created on: 14 wrz 2017
 *      Author: Marcin Antczak
 */

#include "ql_type.h"
#include "ql_system.h"
#include "ql_trace.h"
#include "ql_error.h"
#include "ql_stdlib.h"
#include "ql_fs.h"

#include "bt_task.h"
#include "app_def.h"
#include "app_cfg.h"
#include "cds.h"
#include "crc16.h"
#include "restor_packets.h"
#include "sd_file_lib.h"
#include "rng_file_lib.h"
#include "ecglib.h"
#include "beep.h"
#include "scp.h"


#define IDX_ECG_INIT_TIMESTAMP_LOW           6
#define IDX_ECG_INIT_TIMESTAMP_ML            7
#define IDX_ECG_INIT_TIMESTAMP_MH            8
#define IDX_ECG_INIT_TIMESTAMP_HIGH          9
#define IDX_ECG_INIT_SAMPLING_LOW_BYTE      10
#define IDX_ECG_INIT_SAMPLING_HIGH_BYTE     11
#define IDX_ECG_INIT_SAMPLING_AVG_PULS_TIME 12
#define IDX_ECG_INIT_ECG_CLEAR_BUFFER       13
#define IDX_ECG_INIT_ADDITIONAL_ELEC_COUNT	14
#define IDX_ECG_INIT_ADDITIONAL_ELEC_DATA	15

#define IDX_GET_PULSE_INTERVAL_LOW          6
#define IDX_GET_PULSE_INTERVAL_HIGH         7

#define IDX_ECG_OFFLINE_BACK_TIME          6
#define IDX_ECG_OFFLINE_MEASURE_TIME       7
//#define IDX_ECG_OFFLINE_ELECTROD_LEAD_CODE 8
#define IDX_ECG_OFFLINE_UID_DATA_LEN       8
#define IDX_ECG_OFFLINE_UID_DATA           9

#define IDX_GET_DEV_INFO                   6


#define DEV_BAT_INFO    0x01
#define DEV_ECG_LEADOFF 0x02
#define DEV_ID          0x03
#define DEV_TIME        0x04

#define DEV_ERROR_BATTERY_LOW              0x00

#define CMD_ERROR_MISSING_ECG              0x04

extern u8 ret_imei_bytes(void);
extern u8 *ret_imei_ptr(void);

static u16 frame_number;
static u8 buff_data[1500];

u8 get_unconnected_codes(u16 mask, u8* codes)
{
	u8 unconnected_count = 0;

	if(mask & (1<<0))
	{
		if(GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[0] == SCP_6_I_CODE)
		{
			codes[unconnected_count] = SCP_6_RA_CODE;
		}
		else
		{
			codes[unconnected_count] = SCP_6_RM_CODE;
		}
		unconnected_count++;
	}

	if(mask & (1<<1))
	{
		codes[unconnected_count] = GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[0];
		unconnected_count++;
	}

	if(mask & (1<<2))
	{
		codes[unconnected_count] = GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[1];
		unconnected_count++;
	}

	if((mask & (1<<3)) && (ECG_CHANNELS == 3))
	{
		codes[unconnected_count] = GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[2];
		unconnected_count++;
	}

	if(unconnected_count == (ECG_CHANNELS + 1))
	{
		codes[unconnected_count] = SCP_6_RL_CODE;
		unconnected_count++;
	}

	return unconnected_count;
}

static void app_init(u8 *data)
{
    s32 ret = QL_RET_OK;
    u8 path_file_name[64];
    u32 filesize;
    u32 len;
    X_DEBUG(get_val(DBG_OF_BT_RESTOR), "ACK COMMAND APP_INIT\n");

    BT_Send(create_ack_packet(frame_number), get_packet_size(), &len);

    set_val(UNIX_TIME_STAMP,
            (data[IDX_ECG_INIT_TIMESTAMP_LOW])
            | (data[IDX_ECG_INIT_TIMESTAMP_ML] << 8)
            | (data[IDX_ECG_INIT_TIMESTAMP_MH] << 16)
            | (data[IDX_ECG_INIT_TIMESTAMP_HIGH] << 24));

    GET_OPJ_PTR(ECG_EXAM)->ecg_sampling = data[IDX_ECG_INIT_SAMPLING_LOW_BYTE] | (data[IDX_ECG_INIT_SAMPLING_HIGH_BYTE] << 8);

    set_val(ECG_AVG_PULS_TIME, data[IDX_ECG_INIT_SAMPLING_AVG_PULS_TIME]);

    GET_OPJ_PTR(ECG_EXAM)->ecg_clear_buffer = data[IDX_ECG_INIT_ECG_CLEAR_BUFFER];

    if(data[IDX_ECG_INIT_ADDITIONAL_ELEC_COUNT] == 3)
    {
    	GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[0] = data[IDX_ECG_INIT_ADDITIONAL_ELEC_DATA + 0];
    	GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[1] = data[IDX_ECG_INIT_ADDITIONAL_ELEC_DATA + 1];
    	GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[2] = data[IDX_ECG_INIT_ADDITIONAL_ELEC_DATA + 2];
    }
    else
    {
		#if (CALCULATE_ADDITIONAL_LEADS == 1)
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[0] = SCP_6_I_CODE;
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[1] = SCP_6_II_CODE;
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[2] = SCP_6_III_CODE;
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[3] = SCP_6_aVR_CODE;
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[4] = SCP_6_aVL_CODE;
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[5] = SCP_6_aVF_CODE;
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[6] = data[IDX_ECG_INIT_ADDITIONAL_ELEC_DATA];
		#else
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[0] = SCP_6_I_CODE;
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[1] = SCP_6_II_CODE;
    		GET_OPJ_PTR(ECG_EXAM)->additional_elec_code[2] = data[IDX_ECG_INIT_ADDITIONAL_ELEC_DATA];
		#endif
    }

    X_DEBUG(get_val(DBG_OF_BT_RESTOR), "APP_INIT(): TIMESTAMP %d, SAMPLING %d, AVG_PULSE %d, CLEAR %d\r\n",
            get_val(UNIX_TIME_STAMP), GET_OPJ_PTR(ECG_EXAM)->ecg_sampling,
            get_val(ECG_AVG_PULS_TIME),
            GET_OPJ_PTR(ECG_EXAM)->ecg_clear_buffer);

    if (GET_OPJ_PTR(ECG_EXAM)->ecg_clear_buffer == 0x1)
    {
        if (rng_get_numb_msgs(GET_OPJ_PTR(RNG_ECG)) > 0 )
        {
            X_DEBUG(get_val(DBG_OF_BT_RESTOR), "CLEAR RING BUFFER\r\n");
            rng_read_reposition_in_file_to_nmsg(GET_OPJ_PTR(RNG_ECG),0);
        }
        X_DEBUG(get_val(DBG_OF_BT_RESTOR), "REMOVE ALL *.SCP FILES IF THEY EXIST\n");
        while (search_sd_files(NULL, "*.scp",(char*)path_file_name,(s32*)&filesize) == FOUND_FILE)
        {
            X_DEBUG(get_val(DBG_OF_BT_RESTOR),"DELETE %s\n",path_file_name);
            move_sd_file_to_directory((char*)path_file_name,SD_ARCHIVE_DIR);
            Ql_FS_Delete((char*)path_file_name);
        }
    }

    C_X_ERROR(get_val(DBG_OF_MAIN_TASK), ret, ecg_config(ECG_CHANNELS, ECG_RESOLUTION, GET_OPJ_PTR(ECG_EXAM)->ecg_sampling, 1), "ECG configuration fails ret=%d\n", ret);

    Ql_OS_SendMessage(get_val(MAIN_TASK_ID),MSG_ID_MAIN_TASK_START_ECG_DATA,0,0);
}

static void app_ecg_offline(u8 *data)
{
    u32 len;
    u8 path_file_name[64];
    u32 filesize;

    X_DEBUG(get_val(DBG_OF_BT_RESTOR), "ACK COMMAND ECG OFFLINE\n");

    GET_OPJ_PTR(ECG_EXAM)->ecg_back_time_in_sec = data[IDX_ECG_OFFLINE_BACK_TIME];
    GET_OPJ_PTR(ECG_EXAM)->ecg_measure_time = data[IDX_ECG_OFFLINE_MEASURE_TIME];

    if (GET_OPJ_PTR(ECG_EXAM)->ecg_measure_time>GET_OPJ_PTR(ECG_EXAM)->ecg_back_time_in_sec)
    {
        if (rng_get_numb_msgs(GET_OPJ_PTR(RNG_ECG)) >= GET_OPJ_PTR(ECG_EXAM)->ecg_back_time_in_sec )
        {
            /* ACK command to application */
            BT_Send(create_ack_packet(frame_number), get_packet_size(), &len);

            Ql_memset((char*)GET_OPJ_PTR(ECG_UID),0,get_val(ECG_UID_LEN));
            Ql_memcpy( (char*)GET_OPJ_PTR(ECG_UID), (char*)&data[IDX_ECG_OFFLINE_UID_DATA],data[IDX_ECG_OFFLINE_UID_DATA_LEN]);

            X_DEBUG(get_val(DBG_OF_BT_RESTOR), "REMOVE ALL *.SCP FILES IF THEY EXIST\n");
            while (search_sd_files(NULL, "*.scp",(char*)path_file_name,(s32*)&filesize) == FOUND_FILE)
            {
                X_DEBUG(get_val(DBG_OF_BT_RESTOR),"DELETE %s\n",path_file_name);
                move_sd_file_to_directory((char*)path_file_name,SD_ARCHIVE_DIR);
                Ql_FS_Delete((char*)path_file_name);
            }

            X_DEBUG(get_val(DBG_OF_BT_RESTOR), "APP_ECG_OFFLINE(): ECG_BACK_TIME %d, ECG_MEASURE_TIME %d, UID %s\r\n",
                    GET_OPJ_PTR(ECG_EXAM)->ecg_back_time_in_sec,
                    GET_OPJ_PTR(ECG_EXAM)->ecg_measure_time,
                    GET_OPJ_PTR(ECG_UID));

            X_DEBUG(get_val(DBG_OF_BT_RESTOR), "CORRECT ECG TIME AND ECG BACK TIME\r\n");

            set_val(ECG_TIME_TO_SEND_DONE,GET_OPJ_PTR(ECG_EXAM)->ecg_measure_time-
                                          GET_OPJ_PTR(ECG_EXAM)->ecg_back_time_in_sec);
            set_val(ECG_IN_PROGRESS,TRUE);
            set_val(ECG_SCP_NOT_FINISHED,TRUE);

            X_DEBUG(get_val(DBG_OF_BT_RESTOR), "ENOUGHT ECG BACK TIME IN THE BUFFER\r\n");
            rng_read_reposition_in_file_to_nmsg(GET_OPJ_PTR(RNG_ECG),GET_OPJ_PTR(ECG_EXAM)->ecg_back_time_in_sec);

            Ql_OS_SendMessage(get_val(PROCESS_TASK_ID), MSG_ID_PROCESS_TASK_PLAY_MELODY, ECG_START_SAVE, 0);
        }
        else
        {
            u8 error[2];
            u32 rng_msg = rng_get_numb_msgs(GET_OPJ_PTR(RNG_ECG));
            if (rng_msg > 255)
            {
                rng_msg = 255;
            }
            error[0] = 0x05;
            error[1] = (u8)(rng_msg&0xFF);
            BT_Send(create_cmd_error_packet(frame_number, error, sizeof(error)),
                    get_packet_size(), &len);
            X_DEBUG(TRUE,"NOT ENOUGH ECG RING BUFFER SAMPLES FOR ECG BACK TIME\n");
        }
    }
    else
    {
        u8 error[] = " ECG_BACK_TIME IS GREATER OR EQUAL THAN ECG_MEASURE_TIME\n";
        error[0] = 0x2;
        BT_Send(create_dev_error_packet(frame_number, error,
                                        sizeof(error)),
                get_packet_size(), &len);
        X_DEBUG(TRUE,"%s\n",error);
    }
}

#define MAX_SIZE_OF_DATA_TO_SEND 1450
static u8 send_data_file[MAX_SIZE_OF_DATA_TO_SEND];

void send_scp_file(char *path_file_name, u32 size_to_send)
{
    u16 bytes_to_send = MAX_SIZE_OF_DATA_TO_SEND;
    u32 m_nSentLen = 0;
    u32 m_remain_len = size_to_send;
    s32 read_data_file_bytes;
    u32 send_bytes;
    u8 tiemout_cnt = 0;
    u8 validate_packet_cnt = 0;
    u32 len;
    u8 *ptrNextData;
    set_val(BT_STATES,BT_SEND_SCP_CMD);
    do
    {
        if (Ql_GPIO_GetLevel(PINNAME_CTS) == PINLEVEL_LOW)
        {
            Ql_Sleep(20);
        }

        read_sd_file_to_send(path_file_name,m_nSentLen,send_data_file,sizeof(send_data_file),&read_data_file_bytes);

        if (m_remain_len < bytes_to_send)
        {
            bytes_to_send = m_remain_len;
        }

        BT_Send(create_scp_chunk_to_send_packet(frame_number, send_data_file, read_data_file_bytes),
                get_packet_size(),
                &send_bytes);

        X_DEBUG(get_val(DBG_OF_BT_RESTOR),
                        "<-- FRAME NR %d, BYTES READ %d, SEND BYTES %d, REMAIN BYTES %d, SEND BYTES %d -->\r\n",
                        frame_number, read_data_file_bytes ,send_bytes, m_remain_len, m_nSentLen);

        if (m_remain_len > MAX_SIZE_OF_DATA_TO_SEND)
        {
            char *ptrDebug;
            if (BT_Read(buff_data,sizeof(buff_data),&len,5) == TIMEOUT_READ)
            {
                X_DEBUG(get_val(DBG_OF_BT_RESTOR),"ACK IS NOT OBTAINED FOR PACKET %d\n",frame_number);
                if (tiemout_cnt < 3)
                {
                    tiemout_cnt++;
                    continue;
                }
                else
                {
                    tiemout_cnt = 0;
                    u8 error[] = " TIMEOUT 15s DURING WAIT FOR ACK FROM APP. TWO ATTEMPT OF SEND THE SAME DATA\n";
                    frame_number++;
                    error[0] = 0x2;
                    BT_Send(create_dev_error_packet(frame_number, error, sizeof(error)),get_packet_size(), &len);
                    X_DEBUG(TRUE,"%s\n",error);
                    break;
                }
            }
            else
            {
                tiemout_cnt = 0;
            }

            if ((is_protocol_valid(buff_data,len,&ptrNextData,&ptrDebug) == FALSE) || (buff_data[IDX_PACKET_TYPE] != APP_ACK))
            {
                X_DEBUG(get_val(DBG_OF_BT_RESTOR),"ACK PACKET IS NOT VALID OR INCORRECT PACKET %d\n",frame_number);
                X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",ptrDebug);
                if (validate_packet_cnt < 3)
                {
                    validate_packet_cnt++;
                    continue;
                }
                else
                {
                    u8 error[] = " ACK NOT RECIVED DURING SENDING CHUNK OF SCP FILES OR INVALID PACKET OBTAINED. TWO ATTEMPT OF SEND THE SAME DATA\n";
                    error[0] = 0x2;
                    frame_number++;
                    BT_Send(create_dev_error_packet(frame_number, error, sizeof(error)),get_packet_size(), &len);
                    validate_packet_cnt = 0;
                    X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                    break;
                }
            }
            else
            {
                validate_packet_cnt = 0;
                frame_number = buff_data[IDX_FRAME_LOW_BYTE] | (buff_data[IDX_FRAME_HIGH_BYTE] << 8);
            }
        }
        frame_number++;

        if (read_data_file_bytes == m_remain_len) //send compelete
        {
            m_remain_len = 0;
            m_nSentLen += read_data_file_bytes;
            break;
        }
        else if (read_data_file_bytes < m_remain_len) //continue send, do not send all data
        {
            m_remain_len -= read_data_file_bytes;
            m_nSentLen += read_data_file_bytes;
        }
    }
    while (1);

    X_DEBUG(get_val(DBG_OF_BT_RESTOR),"FILE SEND %s\n",path_file_name);
}

static void app_get_scp(u8* data)
{
    u32 len;
    char path_file_name[64];
    s32 filesize;
    X_DEBUG(get_val(DBG_OF_BT_RESTOR), "ACK COMMAND APP_GET_SCP\n");

    Ql_memset(path_file_name,0,sizeof(path_file_name));  filesize = 0;
    if (search_sd_files(NULL, "*.scp",path_file_name,&filesize) == FOUND_FILE)
    {
        /* ACK command to application */
        BT_Send(create_ack_packet(frame_number), get_packet_size(), &len);
        send_scp_file(path_file_name,filesize);

    }
    else
    {
        u8 error = CMD_ERROR_MISSING_ECG;
        BT_Send(create_cmd_error_packet(frame_number, &error, sizeof(error)), get_packet_size(), &len);
        X_DEBUG(get_val(DBG_OF_BT_RESTOR),"MISSING SCP ECG FILE\n");
    }

}

s32 bt_send_batery_lvl(u8 lvl)
{
	u32 len;
	s32 ret;
	u8 error[2] = {DEV_ERROR_BATTERY_LOW, lvl};

	X_DEBUG(get_val(DBG_OF_BT_RESTOR),"Sending battery lvl low: %d\n", lvl);
	ret = BT_Send(create_dev_error_packet(frame_number, error, sizeof(error)), get_packet_size(), &len);

	return ret;
}

static void pulse_timer_handler(u32 timerId, void* param)
{
    u32 len;
    frame_number++;
    BT_Send(create_pulse_packet(frame_number, (u16) get_val(ECG_AVG_PULS)), get_packet_size(), &len);

}

static void app_get_pulse(u8 *data)
{
    u32 len;
    static bool register_timer = FALSE;
    u16 interval = data[IDX_GET_PULSE_INTERVAL_LOW] | (data[IDX_GET_PULSE_INTERVAL_HIGH] << 8);

    if (interval == 0)
    {
        Ql_Timer_Stop(PULSE_TIMER_ID);
    }

    /* Register timer which send PULSE */
    if (register_timer == FALSE)
    {
        if (Ql_Timer_Register(PULSE_TIMER_ID, pulse_timer_handler, NULL) == QL_RET_OK)
        {
            X_DEBUG(get_val(DBG_OF_BT_RESTOR), "BT_TASK_PULSE_TIMER_ID IS REGISTERED\n");
            register_timer = TRUE;
        }
        else
        {
            X_DEBUG(TRUE, "BT_TASK_PULSE_TIMER_ID IS NOT REGISTERED\n");
        }
    }

    X_DEBUG(get_val(DBG_OF_BT_RESTOR), "ACK COMMAND APP_GET_PULSE\n");
    /* ACK command to application */
    BT_Send(create_ack_packet(frame_number), get_packet_size(), &len);

    if (interval != 0)
    {
        /* Tart timer to send PULSE */
        frame_number++;
        BT_Send(create_pulse_packet(frame_number, (u16) get_val(ECG_AVG_PULS)), get_packet_size(), &len);
        Ql_Timer_Start(PULSE_TIMER_ID, interval * 100, TRUE);
        X_DEBUG(get_val(DBG_OF_BT_RESTOR), "SEND PULS INTERVAL %d. RUN SYSTEM TIMER \n", interval * 100);
        Ql_OS_SendMessage(get_val(BT_TASK_ID), MSG_ID_BT_TASK_TIMEOUT_STOP, 0, 0);
    }
    else
    {
        frame_number++;
        BT_Send(create_pulse_packet(frame_number, (u16) get_val(ECG_AVG_PULS)), get_packet_size(), &len);
        X_DEBUG(get_val(DBG_OF_BT_RESTOR), "STOP SENDING PULS INTERVAL\n");
    }
}

static void app_ecg_end(u8 *data)
{
    u32 len;

    X_DEBUG(get_val(DBG_OF_BT_RESTOR), "ACK COMMAND APP_ECG_END\n");

    BT_Send(create_ack_packet(frame_number), get_packet_size(), &len);

    Ql_OS_SendMessage(get_val(PROCESS_TASK_ID), MSG_ID_PROCESS_TASK_PLAY_MELODY, AFTER_GPS_FIXED, 0);
    Ql_OS_SendMessage(get_val(MAIN_TASK_ID), MSG_ID_MAIN_TASK_STOP_ECG_DATA,0,0);
    Ql_OS_SendMessage(get_val(BT_TASK_ID), MSG_ID_BT_TASK_TIMEOUT_STOP, 00,0);
    Ql_OS_SendMessage(get_val(BT_TASK_ID), MSG_ID_BT_TASK_TIMEOUT_START, 60,0);
}

static void app_ecg_offline_stop(u8 *data)
{
    u32 len;

    X_DEBUG(get_val(DBG_OF_BT_RESTOR), "ACK COMMAND APP_ECG_OFFLINE_STOP\n");

    BT_Send(create_ack_packet(frame_number), get_packet_size(), &len);

    set_val(ECG_IN_PROGRESS,FALSE);
    set_val(ECG_SCP_NOT_FINISHED,FALSE);

    Ql_OS_SendMessage(get_val(PROCESS_TASK_ID), MSG_ID_PROCESS_TASK_PLAY_MELODY, ECG_START_SAVE, 0);
}

static void restor_protocol(void)
{
    static bool app_init_flag = FALSE;
    s32 ret;
    u32 len;
    u8 *ptrData;
    u8 *ptrNextData;

    if (get_val(BT_STATES) > BT_PAIR)
    {
        set_val(BT_STATES,BT_WAIT_FOR_CMD);
    }

    /* Wait from msg from other task */
    ret = BT_Read(buff_data, sizeof(buff_data), &len, BT_READ_FOREVER);

    set_val(BT_STATES,BT_PROCESSING_CMD);

    ptrData = buff_data;
    ptrNextData = NULL;

    if (Ql_strcmp((char*)buff_data,MAIN_TASK_ID_NAME) == 0)
    {
        if (ret == MSG_ID_PROTOCOL_TASK_SEND_DONE)
        {
            X_DEBUG(get_val(DBG_OF_BT_RESTOR), "ECG CAPTURED. SEND DONE TO APPLICATION\n");
            u32 len;
            frame_number++;
            BT_Send(create_ecg_offline_done_packet(frame_number),
                    get_packet_size(), &len);
        }
        else
        {
            u8 error[] = " ECG NOT CAPTURED. ECG MODULE DOES NOT SEND NOTIFICATION\n";
            frame_number++;
            error[0] = 0x2;
            BT_Send(create_dev_error_packet(frame_number, error, sizeof(error)),get_packet_size(), &len);
            X_DEBUG(TRUE,"%s\n",error);
        }
    }
    else
    {
        char *ptrDebug;
        if (is_protocol_valid(ptrData, len, &ptrNextData,&ptrDebug) == FALSE)
        {
            X_DEBUG(TRUE, "PACKET IS NOT VALID: %s\n",ptrDebug);
            ptrDebug[0] = 0x2;
            BT_Send(create_dev_error_packet(frame_number, (u8*)ptrDebug, Ql_strlen((char*)ptrDebug)),get_packet_size(), &len);
        }
        else
        {
            frame_number = (buff_data[IDX_FRAME_LOW_BYTE]) | (buff_data[IDX_FRAME_HIGH_BYTE] << 8);
            switch (buff_data[IDX_PACKET_TYPE])
            {
                case APP_INIT:
                {
                    if (app_init_flag == FALSE)
                    {
                        app_init(buff_data);
                        app_init_flag = TRUE;
                    }
                    else
                    {
                        u8 error[] = " APP INIT ALREADY SET. SEND APP ECG_END COMMAND TO STOP AND THEN SEND APP INIT CMD\n";
                        error[0] = 0x2;
                        BT_Send(create_dev_error_packet(frame_number, error,
                                                        sizeof(error)),
                                get_packet_size(), &len);
                        X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);

                        Ql_OS_SendMessage(get_val(MAIN_TASK_ID),MSG_ID_MAIN_TASK_START_ECG_DATA,0,0);
                    }
                }
                break;

                case APP_ECG_OFFLINE:
                {
                    if (app_init_flag == TRUE)
                    {
                        if (get_val(ECG_IN_PROGRESS) == TRUE)
                        {
                            u8 error[2];
                            u32 time = get_val(ECG_TIME_TO_SEND_DONE);
                            if (time > 255)
                            {
                                time = 255;
                            }
                            error[0] = 0x03;
                            error[1] = (u8)(time&0xFF);
                            BT_Send(create_cmd_error_packet(frame_number, error, sizeof(error)),
                                    get_packet_size(), &len);
                            X_DEBUG(TRUE,"ECG IN PROGRESS. TIME TO FINISH %d\n",time);
                        }
                        else
                        {
                            app_ecg_offline(buff_data);
                        }
                    }
                    else
                    {
                        u8 error[] = " SEND APP INIT CMD BEFOR APP_ECG_OFFLINE\n";
                        error[0] = 0x2;
                        BT_Send(create_dev_error_packet(frame_number, error,
                                                        sizeof(error)),
                                get_packet_size(), &len);
                        X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                    }
                }
                break;

                case APP_GET_SCP_INFO:
                {
                    if (app_init_flag == TRUE)
                    {
                        u8 path_file_name[64];
                        u32 filesize;
                        if (search_sd_files(NULL, "*.scp",(char*)path_file_name,(s32*)&filesize) == FOUND_FILE)
                        {
                            u16 crc;
                            s32 rd_len;
                            read_sd_file_to_send((char*)path_file_name,0,(u8*)&crc,2,&rd_len);
                            BT_Send(create_scp_info_done_packet(frame_number,filesize,crc), get_packet_size(), &len);
                        }
                        else
                        {
                            u8 error = CMD_ERROR_MISSING_ECG;
                            BT_Send(create_cmd_error_packet(frame_number, &error, sizeof(error)), get_packet_size(), &len);
                            X_DEBUG(get_val(DBG_OF_BT_RESTOR),"MISSING SCP ECG FILE\n");
                        }
                    }
                    else
                    {
                        u8 error[] = " SEND APP INIT CMD BEFOR APP_GET_SCP_INFO\n";
                        error[0] = 0x2;
                        BT_Send(create_dev_error_packet(frame_number, error,
                                                        sizeof(error)),
                                get_packet_size(), &len);
                        X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                    }
                }
                break;

                case APP_GET_SCP:
                {
                    if (app_init_flag == TRUE)
                    {
                        app_get_scp(buff_data);
                    }
                    else
                    {
                        u8 error[] = " SEND APP INIT CMD BEFOR APP_GET_SCP\n";
                        error[0] = 0x2;
                        BT_Send(create_dev_error_packet(frame_number, error,
                                                        sizeof(error)),
                                get_packet_size(), &len);
                        X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                    }
                }
                break;

                case APP_SCP_DONE:
                {
                    if (app_init_flag == TRUE)
                    {
                        bool found = FALSE;
                        u8 path_file_name[64];
                        u32 filesize;
                        X_DEBUG(get_val(DBG_OF_BT_RESTOR),"APP_SCP_DONE COMMAND\n");

                        while (search_sd_files(NULL, "*.scp",(char*)path_file_name,(s32*)&filesize) == FOUND_FILE)
                        {
                            found = TRUE;
                            X_DEBUG(get_val(DBG_OF_BT_RESTOR),"DELETE %s\n",path_file_name);
                            move_sd_file_to_directory((char*)path_file_name,SD_ARCHIVE_DIR);
                            Ql_FS_Delete((char*)path_file_name);
                        }

                        if (found == TRUE)
                        {
                            BT_Send(create_ack_packet(frame_number), get_packet_size(), &len);
                        }
                        else
                        {
                            u8 error = CMD_ERROR_MISSING_ECG;
                            BT_Send(create_cmd_error_packet(frame_number, &error, sizeof(error)), get_packet_size(), &len);
                            X_DEBUG(get_val(DBG_OF_BT_RESTOR),"MISSING SCP ECG FILE\n");
                        }
                    }
                    else
                    {
                        u8 error[] = "SEND APP INIT CMD BEFOR APP_SCP_DONE\n";
                        BT_Send(create_dev_error_packet(frame_number, error,
                                                        sizeof(error)),
                                get_packet_size(), &len);
                        X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                    }
                }
                break;

                case APP_GET_PULSE:
                {
                    if (app_init_flag == TRUE)
                    {
                        app_get_pulse(buff_data);
                    }
                    else
                    {
                        u8 error[] = " SEND APP INIT CMD BEFOR APP_GET_PULSE\n";
                        error[0] = 0x2;
                        BT_Send(create_dev_error_packet(frame_number, error,
                                                        sizeof(error)),
                                get_packet_size(), &len);
                        X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                    }
                }
                break;

                case APP_ECG_ONLINE:
                {
                    u8 error[] = " APP_ECG_ONLINE CMD NOT SUPPORTED\n";
                    error[0] = 0x2;
                    BT_Send(create_dev_error_packet(frame_number, error,
                                                    sizeof(error)),
                            get_packet_size(), &len);
                    X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                }
                break;

                case APP_ECG_ONLINE_STOP:
                {
                    u8 error[] = " APP_ECG_ONLINE CMD NOT SUPPORTED\n";
                    error[0] = 0x2;
                    BT_Send(create_dev_error_packet(frame_number, error,
                                                    sizeof(error)),
                            get_packet_size(), &len);

                    X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                }
                break;

                case APP_ECG_END:
                {
                    if (get_val(ECG_IN_PROGRESS) == TRUE)
                    {
                        u8 error[2];
                        u32 time = get_val(ECG_TIME_TO_SEND_DONE);
                        if (time > 255)
                        {
                            time = 255;
                        }
                        error[0] = 0x03;
                        error[1] = (u8)(time&0xFF);
                        BT_Send(create_cmd_error_packet(frame_number, error, sizeof(error)),
                                get_packet_size(), &len);
                        X_DEBUG(TRUE,"ECG IN PROGRESS. TIME TO FINISH %d\n",time);
                    }
                    else
                    {
                        app_ecg_end(buff_data);
                        app_init_flag = FALSE;
                    }
                }
                break;

                case APP_GET:
                {
                	switch(buff_data[IDX_GET_DEV_INFO])
                	{
                		case DEV_BAT_INFO:
                		{
							u8 batinfo[] = {DEV_BAT_INFO,(u8)get_val(BAT_CAP)};
							BT_Send(create_cmd_generic_packet(frame_number, DEV_GET_ANS ,batinfo, sizeof(batinfo)),
									get_packet_size(), &len);

						}
                			break;
                		case DEV_ECG_LEADOFF:
                		{
                			u8 leadoff[16];
                			u8 count;

                			count = get_unconnected_codes(get_val(UNCONNECTED_ELEC_MASK), &leadoff[2]);
                			leadoff[0] = DEV_ECG_LEADOFF;
                			leadoff[1] = count;
							BT_Send(create_cmd_generic_packet(frame_number, DEV_GET_ANS ,leadoff, 2+count),
									get_packet_size(), &len);
                		}
                			break;
                		case DEV_ID:
                		{
                			u8 imei[48];
                			Ql_memset(imei,0,sizeof(imei));

                			imei[0] = DEV_ID;
                			imei[1] = ret_imei_bytes();
                			Ql_strcpy((char*)&imei[2],(char*)ret_imei_ptr());

                			BT_Send(create_cmd_generic_packet(frame_number, DEV_GET_ANS ,imei, 2+imei[1]),
									get_packet_size(), &len);

                		}
                			break;
                		case DEV_TIME:
                		{
                			u8 timestampvec[5];
                			u32 timestamp = get_val(UNIX_TIME_STAMP);
                			timestampvec[0]=DEV_TIME;
                			timestampvec[1]=((timestamp >>  0)&0xFF);
                			timestampvec[2]=((timestamp >>  8)&0xFF);
                			timestampvec[3]=((timestamp >> 16)&0xFF);
                			timestampvec[4]=((timestamp >> 24)&0xFF);
                            BT_Send(create_cmd_generic_packet(frame_number, DEV_GET_ANS ,timestampvec, sizeof(timestampvec)),
                                    get_packet_size(), &len);
                		}
                			break;
                		default:
                			{
								u8 error[48] = " \n";
								error[0] = 0x2;
								Ql_memset(error,0,sizeof(error));
								Ql_sprintf((char*) error, "INCORRECT APP_GET ID INFO: %d",buff_data[IDX_GET_DEV_INFO]);
								BT_Send(create_dev_error_packet(frame_number, error,
																Ql_strlen((char*)error)),
										get_packet_size(), &len);
								X_DEBUG(get_val(DBG_OF_BT_RESTOR),"%s\n",error);
                		    }
                			break;
                	}
                }
				break;
                case APP_ECG_OFFLINE_STOP:
				{
					app_ecg_offline_stop(buff_data);
				}
				break;

                default:
                break;
            }
        }
    }
}


void bt_task_protocol(s32 taskId)

{
    set_val(BT_TASK_PROTOCOL_ID, taskId);
    /* Start message loop of this task */
    while (TRUE)
    {
        restor_protocol();
    }
}

void send_disconnected_electrode(u16 code)
{
    u32 len;
    u8 error[16];
    u8 count;

    count = get_unconnected_codes(code, &error[2]);
    error[0] = 0x01;
    error[1] = count;
    frame_number++;
    BT_Send(create_dev_error_packet(frame_number, error, 2+count), get_packet_size(), &len);
    X_DEBUG(TRUE, "ELECTRODE DISCONNECTED %d\n", code);
}
