/*
 * restor_protocol.h
 *
 *  Created on: 14 wrz 2017
 *      Author: Marcin Antczak
 */

#ifndef RESTOR_PROTOCOL_H_
#define RESTOR_PROTOCOL_H_

#define APP_ACK              0x01
#define DEV_ACK              0x01
#define DEV_ECG_CMD_ERROR    0x02
#define DEV_ECG_DEV_ERROR    0x03
#define APP_INIT             0x04
#define APP_ECG_OFFLINE      0x05
#define DEV_ECG_OFFLINE_DONE 0x06
#define APP_GET_SCP_INFO     0x07
#define DEV_SCP_INFO         0x08
#define APP_GET_SCP          0x09
#define DEV_SCP_CHUNK        0x0A
#define APP_SCP_DONE         0x0B
#define APP_GET_PULSE        0x0C
#define DEV_PULS_VALUE       0x0D
#define APP_ECG_ONLINE       0x0E
#define DEV_ECG_ONLINE_INFO  0x0F
#define DEV_ECG_ONLINE_DATA  0x10
#define APP_ECG_ONLINE_STOP  0x11
#define APP_ECG_END          0x12
#define APP_GET              0x13
#define DEV_GET_ANS          0x14
#define APP_ECG_OFFLINE_STOP 0x15


#define IDX_STX_BYTE        0
#define IDX_PACKET_TYPE     1
#define IDX_FRAME_LOW_BYTE  2
#define IDX_FRAME_HIGH_BYTE 3
#define IDX_MSG_LEN_LOW     4
#define IDX_MSG_LEN_HIGH    5

u8* create_ack_packet(u16 frame_number);
u8* create_dev_error_packet(u16 frame_number, u8 *ptr, u16 size);
u8* create_cmd_error_packet(u16 frame_number, u8 *ptr, u16 size);
u8* create_ecg_offline_done_packet(u16 frame_number);
u8* create_scp_info_done_packet(u16 frame_number, u32 file_size, u16 crcfile);
u8* create_scp_chunk_to_send_packet(u16 frame_number, u8 *ptr, u16 size);
u8* create_pulse_packet(u16 frame_number, u8 pulse);
u8* create_cmd_generic_packet(u16 frame_number, u8 cmd, u8 *ptr, u16 size);

u8* ecg_online_info(u16 frame_number, u16 avm, u8 nr_channels, u8 *ptr, u8 size);
u8* ecg_online_data(u16 frame_number, u16 avm, u8 nr_channels, u8 *ptr, u8 size);
u16 get_packet_size(void);

bool is_protocol_valid(u8 *pdata, u32 len, u8**pNextData, char **pdebug);

s32 get_packet_app_len(void);
u8* get_valid_packet_app_type_ptr(void);

#endif /* CUSTOM_RESTOR_PROTOCOL_H_ */
